import { Test, TestingModule } from '@nestjs/testing';
import { HealthController } from './health.controller';

describe('HealthController', () => {
  let controller: HealthController;

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      controllers: [HealthController],
    }).compile();

    controller = module.get<HealthController>(HealthController);
  });

  it('should be defined', () => {
    expect(controller).toBeDefined();
  });

  describe('health', () => {
    it('should return UP status', () => {
      const result = controller.health();
      expect(result).toEqual({ status: 'UP' });
    });
  });

  describe('ready', () => {
    it('should return READY status', () => {
      const result = controller.ready();
      expect(result).toEqual({ status: 'READY' });
    });
  });

  describe('metrics', () => {
    it('should return metrics object', () => {
      const result = controller.metrics();
      expect(result).toHaveProperty('requests_total');
      expect(result).toHaveProperty('errors_total');
      expect(result).toHaveProperty('uptime');
      expect(result).toHaveProperty('memory');
    });
  });
});