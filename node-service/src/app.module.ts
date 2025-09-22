import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { HttpModule } from '@nestjs/axios';
import { AppController } from './app.controller';
import { AppService } from './app.service';
import { GreetingController } from './greeting/greeting.controller';
import { GreetingService } from './greeting/greeting.service';
import { HealthController } from './health/health.controller';

@Module({
  imports: [
    ConfigModule.forRoot({
      isGlobal: true,
    }),
    HttpModule,
  ],
  controllers: [AppController, GreetingController, HealthController],
  providers: [AppService, GreetingService],
})
export class AppModule {}