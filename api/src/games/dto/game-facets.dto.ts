import { ApiProperty, ApiSchema } from '@nestjs/swagger';

@ApiSchema({ name: 'FacetValue' })
export class FacetValueDto {
  @ApiProperty({ example: 'PS2' })
  value: string;

  @ApiProperty({ type: 'integer', example: 42 })
  count: number;
}

/**
 * Distinct values present in the user's collection with their counts. Clients
 * use them to offer only meaningful filter options and form suggestions.
 */
@ApiSchema({ name: 'GameFacets' })
export class GameFacetsDto {
  @ApiProperty({
    type: 'integer',
    description: 'Number of records in the collection.',
  })
  totalItems: number;

  @ApiProperty({ type: [FacetValueDto] })
  platforms: FacetValueDto[];

  @ApiProperty({ type: [FacetValueDto] })
  statuses: FacetValueDto[];

  @ApiProperty({ type: [FacetValueDto] })
  genres: FacetValueDto[];

  @ApiProperty({ type: [FacetValueDto] })
  publishers: FacetValueDto[];

  @ApiProperty({ type: [FacetValueDto] })
  developers: FacetValueDto[];

  @ApiProperty({ type: [FacetValueDto] })
  storageLocations: FacetValueDto[];

  @ApiProperty({ type: 'integer', nullable: true })
  releaseYearMin: number | null;

  @ApiProperty({ type: 'integer', nullable: true })
  releaseYearMax: number | null;
}
