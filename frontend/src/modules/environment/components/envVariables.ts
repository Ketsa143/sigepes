/** HU-14 · Variables ambientales que se pueden graficar. */
export type EnvVariable = 'sstC' | 'chlorophyllMgM3' | 'waveHeightM';

export const VARIABLE: Record<EnvVariable, { label: string; short: string; unit: string; digits: number }> = {
  sstC: { label: 'Temperatura del mar', short: 'Temperatura', unit: '°C', digits: 1 },
  chlorophyllMgM3: { label: 'Clorofila-a', short: 'Clorofila', unit: 'mg/m³', digits: 2 },
  waveHeightM: { label: 'Altura de ola', short: 'Oleaje', unit: 'm', digits: 1 },
};
