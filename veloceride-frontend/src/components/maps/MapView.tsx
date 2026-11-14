import { useEffect, useMemo } from 'react';
import L from 'leaflet';
import { MapContainer, Marker, Polyline, TileLayer, Tooltip, useMap, useMapEvents } from 'react-leaflet';
import { cn } from '@/utils/cn';
import { useThemeStore } from '@/theme/useTheme';

export interface MapPoint {
  lat: number;
  lng: number;
  label: string;
  kind: 'start' | 'end' | 'vehicle' | 'me';
}

const DEFAULT_CENTER: [number, number] = [22.5, 79]; // India overview

/** Div-based markers avoid Leaflet's image-path issues under bundlers and match the theme. */
function icon(kind: MapPoint['kind']) {
  // Colours come from theme tokens (.vr-marker--* in index.css), so markers follow the theme.
  const size = kind === 'vehicle' || kind === 'me' ? 18 : 14;
  return L.divIcon({
    className: '',
    html: `<span class="vr-marker vr-marker--${kind}${kind === 'vehicle' ? ' vr-pulse' : ''}" style="width:${size}px;height:${size}px"></span>`,
    iconSize: [size, size],
    iconAnchor: [size / 2, size / 2],
  });
}

function FitBounds({ points, follow }: { points: MapPoint[]; follow?: MapPoint | null }) {
  const map = useMap();
  const key = points.map((p) => `${p.lat.toFixed(4)},${p.lng.toFixed(4)}`).join('|');
  useEffect(() => {
    if (!points.length) return;
    if (points.length === 1) map.setView([points[0].lat, points[0].lng], 13);
    else map.fitBounds(L.latLngBounds(points.map((p) => [p.lat, p.lng])), { padding: [40, 40], maxZoom: 14 });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, map]);
  useEffect(() => {
    if (follow) map.panTo([follow.lat, follow.lng], { animate: true });
  }, [follow, map]);
  return null;
}

function ClickHandler({ onClick }: { onClick: (lat: number, lng: number) => void }) {
  useMapEvents({ click: (e) => onClick(e.latlng.lat, e.latlng.lng) });
  return null;
}

/**
 * OpenStreetMap view. Shows only the points we actually have (ride coordinates are optional in the
 * backend); the dashed line is a straight connector, not a road route (no routing data exists).
 */
export function MapView({
  points,
  follow,
  onMapClick,
  className,
  connect = true,
}: {
  points: MapPoint[];
  follow?: MapPoint | null;
  onMapClick?: (lat: number, lng: number) => void;
  className?: string;
  connect?: boolean;
}) {
  // SVG stroke needs a concrete colour; re-read the token whenever the theme changes.
  const resolvedTheme = useThemeStore((s) => s.resolved);
  const accent = useMemo(
    () => getComputedStyle(document.documentElement).getPropertyValue('--vr-accent-bright').trim() || '#22C7B5',
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [resolvedTheme],
  );
  const start = points.find((p) => p.kind === 'start');
  const end = points.find((p) => p.kind === 'end');
  const line = useMemo(
    () => (connect && start && end ? ([[start.lat, start.lng], [end.lat, end.lng]] as [number, number][]) : null),
    [connect, start, end],
  );
  return (
    <div className={cn('overflow-hidden rounded-xl border border-line', className)}>
      <MapContainer center={DEFAULT_CENTER} zoom={4} scrollWheelZoom={false} className="h-full min-h-64 w-full">
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />
        {line && <Polyline positions={line} pathOptions={{ color: accent, weight: 2, opacity: 0.7, dashArray: '6 8' }} />}
        {points.map((p) => (
          <Marker key={`${p.kind}-${p.label}`} position={[p.lat, p.lng]} icon={icon(p.kind)} keyboard={false}>
            <Tooltip direction="top" offset={[0, -8]}>
              {p.label}
            </Tooltip>
          </Marker>
        ))}
        <FitBounds points={points} follow={follow} />
        {onMapClick && <ClickHandler onClick={onMapClick} />}
      </MapContainer>
    </div>
  );
}

/** Ride source/destination markers when the driver provided coordinates. */
export function ridePoints(ride: {
  source: string;
  destination: string;
  sourceLatitude?: number;
  sourceLongitude?: number;
  destinationLatitude?: number;
  destinationLongitude?: number;
}): MapPoint[] {
  const pts: MapPoint[] = [];
  if (ride.sourceLatitude != null && ride.sourceLongitude != null) {
    pts.push({ lat: ride.sourceLatitude, lng: ride.sourceLongitude, label: ride.source, kind: 'start' });
  }
  if (ride.destinationLatitude != null && ride.destinationLongitude != null) {
    pts.push({ lat: ride.destinationLatitude, lng: ride.destinationLongitude, label: ride.destination, kind: 'end' });
  }
  return pts;
}
