import { Box, Button, Stack, Typography } from '@mui/material';
import { useCallback, useEffect, useRef, useState } from 'react';

const WIDTH = 520;
const HEIGHT = 180;

interface Props {
  label: string;
  /** Firma como data URL PNG, o null si está vacía. */
  onChange: (dataUrl: string | null) => void;
}

/**
 * HU-22 · AC-22.3 · Firma con el dedo o el mouse. Trabaja con eventos de puntero (táctil y mouse) y
 * entrega un PNG pequeño que viaja en el acta, también sin señal.
 */
export function SignaturePad({ label, onChange }: Props) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const drawing = useRef(false);
  const last = useRef<{ x: number; y: number } | null>(null);
  const [signed, setSigned] = useState(false);
  // Ref además del estado: los eventos de puntero llegan antes de que React vuelva a pintar.
  const hasInk = useRef(false);

  useEffect(() => {
    const context = canvasRef.current?.getContext('2d');
    if (!context) return;
    context.lineWidth = 2.4;
    context.lineCap = 'round';
    context.lineJoin = 'round';
    context.strokeStyle = '#0A2540';
  }, []);

  const point = (event: React.PointerEvent<HTMLCanvasElement>) => {
    const rect = event.currentTarget.getBoundingClientRect();
    return {
      x: ((event.clientX - rect.left) / rect.width) * WIDTH,
      y: ((event.clientY - rect.top) / rect.height) * HEIGHT,
    };
  };

  const start = (event: React.PointerEvent<HTMLCanvasElement>) => {
    event.currentTarget.setPointerCapture(event.pointerId);
    drawing.current = true;
    last.current = point(event);
  };

  const move = (event: React.PointerEvent<HTMLCanvasElement>) => {
    if (!drawing.current || !last.current) return;
    const context = canvasRef.current?.getContext('2d');
    if (!context) return;
    const next = point(event);
    context.beginPath();
    context.moveTo(last.current.x, last.current.y);
    context.lineTo(next.x, next.y);
    context.stroke();
    last.current = next;
    if (!hasInk.current) {
      hasInk.current = true;
      setSigned(true);
    }
  };

  const end = () => {
    if (!drawing.current) return;
    drawing.current = false;
    last.current = null;
    if (hasInk.current && canvasRef.current) onChange(canvasRef.current.toDataURL('image/png'));
  };

  const clear = useCallback(() => {
    const canvas = canvasRef.current;
    canvas?.getContext('2d')?.clearRect(0, 0, WIDTH, HEIGHT);
    hasInk.current = false;
    setSigned(false);
    onChange(null);
  }, [onChange]);

  return (
    <Box>
      <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 0.5 }}>
        <Typography variant="body2" fontWeight={700}>{label}</Typography>
        <Button size="small" onClick={clear} disabled={!signed}>Borrar firma</Button>
      </Stack>
      <Box
        component="canvas"
        ref={canvasRef}
        width={WIDTH}
        height={HEIGHT}
        role="img"
        aria-label={signed ? `${label}: firmada` : `${label}: espacio para firmar con el dedo o el mouse`}
        onPointerDown={start}
        onPointerMove={move}
        onPointerUp={end}
        onPointerCancel={end}
        onPointerLeave={end}
        sx={{
          width: '100%', aspectRatio: `${WIDTH} / ${HEIGHT}`, display: 'block', touchAction: 'none', cursor: 'crosshair',
          bgcolor: '#fff', border: 2, borderStyle: 'dashed', borderColor: signed ? 'primary.main' : 'divider', borderRadius: 2,
        }}
      />
      {!signed && <Typography variant="caption" color="text.secondary">Firme dentro del recuadro.</Typography>}
    </Box>
  );
}
