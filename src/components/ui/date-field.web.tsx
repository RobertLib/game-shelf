import { useState } from 'react';

import { parseIsoDate } from '@/lib/format';

import type { DateFieldProps } from './date-field';
import { TextField } from './text-field';

/** The native date picker has no web implementation, so web gets a plain text input. */
export function DateField({ value, onChange, placeholder = 'YYYY-MM-DD', error }: DateFieldProps) {
  const [text, setText] = useState(value ?? '');

  return (
    <TextField
      value={text}
      placeholder={placeholder}
      autoCorrect={false}
      error={error}
      onChangeText={(next) => {
        setText(next);
        if (next.trim() === '') onChange(null);
        else if (parseIsoDate(next.trim())) onChange(next.trim());
      }}
    />
  );
}
