import { Test, TestingModule } from '@nestjs/testing';
import { HttpService } from '@nestjs/axios';
import { GreetingService } from './greeting.service';
import { of, throwError } from 'rxjs';
import { AxiosResponse, AxiosError } from 'axios';
import { HttpException, HttpStatus } from '@nestjs/common';

describe('GreetingService', () => {
  let service: GreetingService;
  let httpService: HttpService;

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        GreetingService,
        {
          provide: HttpService,
          useValue: {
            get: jest.fn(),
            post: jest.fn(),
          },
        },
      ],
    }).compile();

    service = module.get<GreetingService>(GreetingService);
    httpService = module.get<HttpService>(HttpService);
  });

  it('should be defined', () => {
    expect(service).toBeDefined();
  });

  describe('getGreeting', () => {
    it('should return greeting from Java service', async () => {
      const mockResponse: AxiosResponse = {
        data: { message: 'Hello, Test!' },
        status: 200,
        statusText: 'OK',
        headers: {},
        config: {} as any,
      };

      jest.spyOn(httpService, 'get').mockReturnValue(of(mockResponse));

      const result = await service.getGreeting('Test');

      expect(result).toHaveProperty('message', 'Hello, Test!');
      expect(result).toHaveProperty('source', 'node-service');
      expect(result).toHaveProperty('upstreamSource', 'demo-service');
      expect(result).toHaveProperty('timestamp');
    });

    it('should throw HttpException when Java service fails', async () => {
      const error = new Error('Connection refused');
      jest.spyOn(httpService, 'get').mockReturnValue(throwError(() => error));

      await expect(service.getGreeting('Test')).rejects.toThrow(HttpException);
    });
  });

  describe('chainRequest', () => {
    it('should return completion message when depth exceeds 5', async () => {
      const result = await service.chainRequest('Test', 6);

      expect(result).toHaveProperty('message', 'Chain completed at depth 6');
      expect(result).toHaveProperty('originalMessage', 'Test');
      expect(result).toHaveProperty('source', 'node-service');
    });

    it('should call Java service when depth is 5 or less', async () => {
      const mockResponse: AxiosResponse = {
        data: { message: 'Echo: [depth:0] Test' },
        status: 200,
        statusText: 'OK',
        headers: {},
        config: {} as any,
      };

      jest.spyOn(httpService, 'post').mockReturnValue(of(mockResponse));

      const result = await service.chainRequest('Test', 0);

      expect(result).toHaveProperty('message');
      expect(result).toHaveProperty('depth', 1);
      expect(result).toHaveProperty('source', 'node-service');
    });
  });

  describe('simulate', () => {
    it('should handle normal scenario', async () => {
      const mockResponse: AxiosResponse = {
        data: { result: 'success' },
        status: 200,
        statusText: 'OK',
        headers: {},
        config: {} as any,
      };

      jest.spyOn(httpService, 'post').mockReturnValue(of(mockResponse));

      const result = await service.simulate('normal', 0);

      expect(result).toHaveProperty('scenario', 'normal');
      expect(result).toHaveProperty('nodeProcessingTime', 0);
      expect(result).toHaveProperty('source', 'node-service');
    });

    it('should throw error for error scenario', async () => {
      await expect(service.simulate('error', 0)).rejects.toThrow(HttpException);
    });

    it('should add delay when specified', async () => {
      const mockResponse: AxiosResponse = {
        data: { result: 'success' },
        status: 200,
        statusText: 'OK',
        headers: {},
        config: {} as any,
      };

      jest.spyOn(httpService, 'post').mockReturnValue(of(mockResponse));

      const startTime = Date.now();
      await service.simulate('normal', 100);
      const endTime = Date.now();

      expect(endTime - startTime).toBeGreaterThanOrEqual(100);
    });
  });
});