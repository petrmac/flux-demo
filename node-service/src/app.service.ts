import { Injectable } from '@nestjs/common';

@Injectable()
export class AppService {
  getInfo() {
    return {
      service: 'node-service',
      version: process.env.SERVICE_VERSION || '1.0.0',
      environment: process.env.ENVIRONMENT || 'development',
      timestamp: new Date().toISOString(),
      javaServiceUrl: process.env.JAVA_SERVICE_URL || 'http://demo-service:8080',
    };
  }
}