import { Controller, Get } from '@nestjs/common';

@Controller()
export class HealthController {
  @Get('health')
  health() {
    return { status: 'UP' };
  }

  @Get('ready')
  ready() {
    return { status: 'READY' };
  }

  @Get('metrics')
  metrics() {
    // Basic metrics endpoint - can be enhanced with actual metrics
    return {
      requests_total: 0,
      errors_total: 0,
      uptime: process.uptime(),
      memory: process.memoryUsage(),
    };
  }
}