import BlockIcon from '@mui/icons-material/Block';
import DirectionsBoatIcon from '@mui/icons-material/DirectionsBoat';
import MenuBookIcon from '@mui/icons-material/MenuBook';
import ParkIcon from '@mui/icons-material/Park';
import PhishingIcon from '@mui/icons-material/Phishing';
import StraightenIcon from '@mui/icons-material/Straighten';
import VolunteerActivismOutlinedIcon from '@mui/icons-material/VolunteerActivismOutlined';
import type { SvgIconProps } from '@mui/material';
import type { CourseIcon as CourseIconKey } from '../../shared/api/domain.service';

/** HU-19 · AC-19.5 · Íconos grandes y reconocibles para cada curso. */
export function CourseIcon({ icon, ...props }: { icon: CourseIconKey } & SvgIconProps) {
  switch (icon) {
    case 'ruler': return <StraightenIcon {...props} />;
    case 'calendar': return <BlockIcon {...props} />;
    case 'park': return <ParkIcon {...props} />;
    case 'hand': return <VolunteerActivismOutlinedIcon {...props} />;
    case 'fish': return <PhishingIcon {...props} />;
    case 'boat': return <DirectionsBoatIcon {...props} />;
    default: return <MenuBookIcon {...props} />;
  }
}
