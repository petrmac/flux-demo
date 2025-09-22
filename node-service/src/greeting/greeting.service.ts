import { Injectable, Logger, HttpException, HttpStatus } from '@nestjs/common';
import { HttpService } from '@nestjs/axios';
import { firstValueFrom } from 'rxjs';
import { AxiosError } from 'axios';

@Injectable()
export class GreetingService {
  private readonly logger = new Logger(GreetingService.name);
  private readonly javaServiceUrl: string;

  constructor(private readonly httpService: HttpService) {
    this.javaServiceUrl = process.env.JAVA_SERVICE_URL || 'http://demo-service:8080';
  }

  async getGreeting(name: string) {
    try {
      const response = await firstValueFrom(
        this.httpService.get(`${this.javaServiceUrl}/api/greeting`, {
          params: { name },
        }),
      );

      return {
        message: response.data.message,
        timestamp: new Date().toISOString(),
        source: 'node-service',
        upstreamSource: 'demo-service',
        upstreamResponse: response.data,
      };
    } catch (error) {
      this.logger.error(`Failed to call Java service: ${error.message}`);
      throw new HttpException(
        {
          error: 'Failed to fetch greeting from Java service',
          message: error.message,
        },
        HttpStatus.SERVICE_UNAVAILABLE,
      );
    }
  }

  async chainRequest(message: string, depth: number) {
    if (depth > 5) {
      return {
        message: `Chain completed at depth ${depth}`,
        originalMessage: message,
        source: 'node-service',
        timestamp: new Date().toISOString(),
      };
    }

    try {
      const response = await firstValueFrom(
        this.httpService.post(`${this.javaServiceUrl}/api/echo`, {
          message: `[depth:${depth}] ${message}`,
        }),
      );

      return {
        message: response.data.message || response.data,
        depth: depth + 1,
        source: 'node-service',
        upstreamSource: 'demo-service',
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      this.logger.error(`Chain request failed at depth ${depth}: ${error.message}`);
      return {
        message: `Chain interrupted at depth ${depth}`,
        error: error.message,
        source: 'node-service',
        timestamp: new Date().toISOString(),
      };
    }
  }

  async simulate(scenario: string, delay: number) {
    // Add delay if requested
    if (delay > 0) {
      await new Promise(resolve => setTimeout(resolve, delay));
    }

    // Simulate different scenarios
    switch (scenario) {
      case 'error':
        throw new HttpException('Simulated error', HttpStatus.INTERNAL_SERVER_ERROR);

      case 'timeout':
        await new Promise(resolve => setTimeout(resolve, 30000));
        throw new HttpException('Request timeout', HttpStatus.REQUEST_TIMEOUT);

      case 'slow':
        await new Promise(resolve => setTimeout(resolve, 3000));
        break;
    }

    try {
      // Call Java service with reduced delay
      const response = await firstValueFrom(
        this.httpService.post(`${this.javaServiceUrl}/api/simulate`, {
          scenario,
          delay: Math.max(0, delay - 100),
        }),
      );

      return {
        scenario,
        nodeProcessingTime: delay,
        javaResponse: response.data,
        source: 'node-service',
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      if (error instanceof AxiosError) {
        throw new HttpException(
          {
            error: 'Java service simulation failed',
            message: error.message,
            response: error.response?.data,
          },
          error.response?.status || HttpStatus.SERVICE_UNAVAILABLE,
        );
      }
      throw error;
    }
  }
}