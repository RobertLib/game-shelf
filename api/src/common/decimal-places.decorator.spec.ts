import { decimalPlaces } from './decimal-places.decorator.js';

describe('decimalPlaces', () => {
  it.each([
    [0, 0],
    [1500, 0],
    [1299.9, 1],
    [0.29, 2],
    [9_999_999_999.99, 2],
    [1.005, 3],
    [1e-7, 7],
    [1.5e-7, 8],
    [1e21, 0],
    [1.25e21, 0],
  ])('%d has %d', (value, places) => {
    expect(decimalPlaces(value)).toBe(places);
  });
});
