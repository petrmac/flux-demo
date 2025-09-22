import { Controller, Get, Query, Post, Body, Logger } from '@nestjs/common';
import { GreetingService } from './greeting.service';

@Controller('api')
export class GreetingController {
  private readonly logger = new Logger(GreetingController.name);

  constructor(private readonly greetingService: GreetingService) {}

  @Get('greeting')
  async getGreeting(@Query('name') name: string = 'World') {
    this.logger.log(`Processing greeting request for: ${name}`);
    return this.greetingService.getGreeting(name);
  }

  @Post('chain')
  async chainRequest(@Body() body: { message: string; depth?: number }) {
    const { message, depth = 0 } = body;
    this.logger.log(`Chain request at depth ${depth}: ${message}`);
    return this.greetingService.chainRequest(message, depth);
  }

  @Post('simulate')
  async simulate(@Body() body: { scenario: string; delay?: number }) {
    const { scenario = 'normal', delay = 0 } = body;
    this.logger.log(`Simulating scenario: ${scenario} with delay: ${delay}ms`);
    return this.greetingService.simulate(scenario, delay);
  }
}