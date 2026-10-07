import { ApiProperty, ApiSchema } from '@nestjs/swagger';
import { Matches } from 'class-validator';
import { Platform, Region } from '../../generated/prisma/enums.js';

const nullableString = (maxLength: number, example: string) =>
  ({ type: String, nullable: true, maxLength, example }) as const;

export class BarcodeParamDto {
  @ApiProperty({
    pattern: '^[0-9]{8,14}$',
    description: 'EAN / UPC (8–14 digits).',
    example: '045496420055',
  })
  @Matches(/^\d{8,14}$/, { message: 'barcode must contain 8–14 digits' })
  barcode: string;
}

/**
 * Details of the game with a barcode, ready to prefill a new game. Every value
 * fits the limits of `SaveGameRequest`.
 */
@ApiSchema({ name: 'BarcodeLookup' })
export class BarcodeLookupDto {
  @ApiProperty({
    description:
      'The code that was looked up, without padding zeros: UPC-A scanned as EAN-13 has 12 digits.',
    example: '045496420055',
  })
  barcode: string;

  @ApiProperty({ maxLength: 200, example: 'Mario Kart 8 Deluxe' })
  title: string;

  @ApiProperty({ enum: Platform, enumName: 'Platform', nullable: true })
  platform: Platform | null;

  @ApiProperty({ enum: Region, enumName: 'Region', nullable: true })
  region: Region | null;

  @ApiProperty(nullableString(100, "Collector's Edition"))
  edition: string | null;

  @ApiProperty(nullableString(100, 'Racing'))
  genre: string | null;

  @ApiProperty(nullableString(100, 'Nintendo EPD'))
  developer: string | null;

  @ApiProperty(nullableString(100, 'Nintendo'))
  publisher: string | null;

  @ApiProperty({ type: 'integer', nullable: true, example: 2017 })
  releaseYear: number | null;

  @ApiProperty({ type: String, format: 'uri', nullable: true })
  coverImageUrl: string | null;

  @ApiProperty({
    type: [String],
    description: 'Databases the details come from, to be shown as attribution.',
    example: ['UPCitemdb', 'IGDB'],
  })
  sources: string[];
}
