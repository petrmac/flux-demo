import { Test, TestingModule } from '@nestjs/testing';
import { GreetingController } from './greeting.controller';
import { GreetingService } from './greeting.service';

describe('GreetingController', () => {
  let controller: GreetingController;
  let service: GreetingService;

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      controllers: [GreetingController],
      providers: [
        {
          provide: GreetingService,
          useValue: {
            getGreeting: jest.fn(),
            chainRequest: jest.fn(),
            simulate: jest.fn(),
          },
        },
      ],
    }).compile();

    controller = module.get<GreetingController>(GreetingController);
    service = module.get<GreetingService>(GreetingService);
  });

  it('should be defined', () => {
    expect(controller).toBeDefined();
  });

  describe('getGreeting', () => {
    it('should call service with default name', async () => {
      const mockResult = {
        message: 'Hello, World!',
        timestamp: new Date().toISOString(),
        source: 'node-service',
        upstreamSource: 'demo-service',
        upstreamResponse: {}
      };
      jest.spyOn(service, 'getGreeting').mockResolvedValue(mockResult);

      const result = await controller.getGreeting();

      expect(service.getGreeting).toHaveBeenCalledWith('World');
      expect(result).toEqual(mockResult);
    });

    it('should call service with provided name', async () => {
      const mockResult = {
        message: 'Hello, Test!',
        timestamp: new Date().toISOString(),
        source: 'node-service',
        upstreamSource: 'demo-service',
        upstreamResponse: {}
      };
      jest.spyOn(service, 'getGreeting').mockResolvedValue(mockResult);

      const result = await controller.getGreeting('Test');

      expect(service.getGreeting).toHaveBeenCalledWith('Test');
      expect(result).toEqual(mockResult);
    });
  });

  describe('chainRequest', () => {
    it('should call service with message and depth', async () => {
      const mockResult = {
        message: 'Chain result',
        depth: 3,
        source: 'node-service',
        upstreamSource: 'demo-service',
        timestamp: new Date().toISOString()
      };
      jest.spyOn(service, 'chainRequest').mockResolvedValue(mockResult);

      const body = { message: 'Test', depth: 2 };
      const result = await controller.chainRequest(body);

      expect(service.chainRequest).toHaveBeenCalledWith('Test', 2);
      expect(result).toEqual(mockResult);
    });

    it('should default depth to 0 if not provided', async () => {
      const mockResult = {
        message: 'Chain result',
        depth: 1,
        source: 'node-service',
        upstreamSource: 'demo-service',
        timestamp: new Date().toISOString()
      };
      jest.spyOn(service, 'chainRequest').mockResolvedValue(mockResult);

      const body = { message: 'Test' };
      const result = await controller.chainRequest(body);

      expect(service.chainRequest).toHaveBeenCalledWith('Test', 0);
      expect(result).toEqual(mockResult);
    });
  });

  describe('simulate', () => {
    it('should call service with scenario and delay', async () => {
      const mockResult = {
        scenario: 'slow',
        nodeProcessingTime: 1000,
        javaResponse: { result: 'success' },
        source: 'node-service',
        timestamp: new Date().toISOString()
      };
      jest.spyOn(service, 'simulate').mockResolvedValue(mockResult);

      const body = { scenario: 'slow', delay: 1000 };
      const result = await controller.simulate(body);

      expect(service.simulate).toHaveBeenCalledWith('slow', 1000);
      expect(result).toEqual(mockResult);
    });

    it('should use default values if not provided', async () => {
      const mockResult = {
        scenario: 'normal',
        nodeProcessingTime: 0,
        javaResponse: { result: 'success' },
        source: 'node-service',
        timestamp: new Date().toISOString()
      };
      jest.spyOn(service, 'simulate').mockResolvedValue(mockResult);

      const body = { scenario: 'normal' };
      const result = await controller.simulate(body);

      expect(service.simulate).toHaveBeenCalledWith('normal', 0);
      expect(result).toEqual(mockResult);
    });
  });
});