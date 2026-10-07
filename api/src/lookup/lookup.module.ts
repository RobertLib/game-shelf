import { Module } from '@nestjs/common';
import { BarcodeLookupService } from './barcode-lookup.service.js';
import { IgdbClient } from './igdb.client.js';
import { LookupController } from './lookup.controller.js';
import { UpcItemDbClient } from './upcitemdb.client.js';

@Module({
  controllers: [LookupController],
  providers: [BarcodeLookupService, UpcItemDbClient, IgdbClient],
})
export class LookupModule {}
