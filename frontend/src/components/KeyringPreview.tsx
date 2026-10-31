// Live preview of a personalised name keychain: raised letters on an outline base, like the real product.

const COLOURS: Record<string, string> = {
  black: '#1f2328', white: '#f7f7f5', red: '#d6312b', blue: '#1f5fd1', yellow: '#f4c21a', pink: '#ef6fa6',
  green: '#2f9e5b', orange: '#f07a1f', purple: '#7a4fd6', grey: '#8a9099', gray: '#8a9099', gold: '#c9a227',
}

const STYLES: Record<string, { font: string; weight: number; spacing: number; width: number }> = {
  classic: { font: 'Inter, sans-serif', weight: 800, spacing: 0, width: 0.62 },
  script: { font: 'Pacifico, cursive', weight: 400, spacing: 0, width: 0.62 },
  bold: { font: '"Archivo Black", sans-serif', weight: 400, spacing: 1, width: 0.72 },
  rounded: { font: 'Fredoka, sans-serif', weight: 600, spacing: 0, width: 0.58 },
}

export function colourHex(name?: string) {
  return COLOURS[(name ?? '').trim().toLowerCase()] ?? '#1f2328'
}

export function KeyringPreview({ text, colour, style, className = '' }: { text: string; colour?: string; style?: string; className?: string }) {
  const name = (text || 'Name').slice(0, 14)
  const fill = colourHex(colour)
  const s = STYLES[(style ?? 'classic').toLowerCase()] ?? STYLES.classic
  const lightLetters = ['#f7f7f5', '#f4c21a'].includes(fill)
  const base = lightLetters ? '#3b3f8f' : '#ffffff'
  const edge = lightLetters ? '#2b2e6b' : '#d9dde5'
  const size = Math.max(60, Math.min(150, 900 / Math.max(name.length, 4)))
  const width = name.length * size * s.width
  const left = 420 - width / 2

  return (
    <div className={`relative flex items-center justify-center overflow-hidden bg-[radial-gradient(circle_at_30%_20%,#ffffff,#ebebee)] ${className}`}>
      <svg viewBox="0 0 840 840" className="h-full w-full" role="img" aria-label={`Preview: ${name} keychain in ${colour ?? 'black'}, ${style ?? 'classic'} style`}>
        <defs>
          <filter id="kshadow" x="-20%" y="-30%" width="140%" height="180%"><feDropShadow dx="0" dy="16" stdDeviation="14" floodOpacity="0.22" /></filter>
        </defs>
        <g transform="rotate(-10 420 420)">
          {/* chain + ring */}
          <circle cx={left - 70} cy={560} r="70" fill="none" stroke="#a9b0ba" strokeWidth="12" />
          <circle cx={left - 70} cy={560} r="70" fill="none" stroke="#eef1f5" strokeWidth="3" />
          <path d={`M${left - 40} 495 L${left - 18} 450`} stroke="#a9b0ba" strokeWidth="10" strokeLinecap="round" />
          <g filter="url(#kshadow)">
            {/* eyelet */}
            <circle cx={left - 12} cy="440" r="30" fill={base} stroke={edge} strokeWidth="4" />
            <circle cx={left - 12} cy="440" r="11" fill="#ebebee" />
            {/* outline base under the letters */}
            <text x="420" y="430" textAnchor="middle" dominantBaseline="central" fontFamily={s.font} fontWeight={s.weight}
                  fontSize={size} letterSpacing={s.spacing} fill={base} stroke={base} strokeWidth={size * 0.32} strokeLinejoin="round">{name}</text>
            <text x="420" y="430" textAnchor="middle" dominantBaseline="central" fontFamily={s.font} fontWeight={s.weight}
                  fontSize={size} letterSpacing={s.spacing} fill="none" stroke={edge} strokeWidth="3" strokeLinejoin="round" opacity="0.5">{name}</text>
          </g>
          {/* raised letters */}
          <text x="420" y="424" textAnchor="middle" dominantBaseline="central" fontFamily={s.font} fontWeight={s.weight}
                fontSize={size} letterSpacing={s.spacing} fill={fill} stroke="#00000026" strokeWidth="2">{name}</text>
        </g>
      </svg>
      <span className="absolute bottom-3 left-4 text-[11px] text-muted">Preview · actual colours may vary slightly</span>
    </div>
  )
}
