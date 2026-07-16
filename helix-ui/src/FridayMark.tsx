export function FridayMark() {
  return (
    <svg
      aria-label="F.R.I.D.A.Y. operations emblem"
      viewBox="0 0 96 96"
      className="h-full w-full"
      role="img"
    >
      <defs>
        <radialGradient id="fridayCore" cx="50%" cy="42%" r="58%">
          <stop offset="0" stopColor="#d7f4ff" />
          <stop offset=".25" stopColor="#5fc8ff" />
          <stop offset=".58" stopColor="#236dff" />
          <stop offset="1" stopColor="#071b5e" />
        </radialGradient>
        <linearGradient id="fridayMetal" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#a7e9ff" />
          <stop offset=".28" stopColor="#288dff" />
          <stop offset=".72" stopColor="#1746ca" />
          <stop offset="1" stopColor="#59c8ff" />
        </linearGradient>
        <filter id="fridayGlow" x="-40%" y="-40%" width="180%" height="180%">
          <feGaussianBlur stdDeviation="2.4" result="blur" />
          <feMerge><feMergeNode in="blur" /><feMergeNode in="SourceGraphic" /></feMerge>
        </filter>
      </defs>
      <path d="M18 7h50l11 11v11h8v38h-8v11L68 89H18L7 78V67H1V29h6V18z" fill="rgba(2,8,22,.92)" stroke="#2474ff" strokeWidth="1.6" />
      <path d="M22 13h42l8 8M13 23v20M13 53v20M22 83h42l8-8M83 24v18M83 54v18" fill="none" stroke="#49b8ff" strokeWidth="1" opacity=".75" />
      <circle cx="48" cy="48" r="31" fill="none" stroke="#1648ba" strokeWidth=".8" strokeDasharray="3 4" />
      <ellipse cx="48" cy="48" rx="37" ry="17" fill="none" stroke="#247cff" strokeWidth="1" transform="rotate(-18 48 48)" opacity=".8" />
      <ellipse cx="48" cy="48" rx="35" ry="12" fill="none" stroke="#38aaff" strokeWidth=".8" transform="rotate(58 48 48)" opacity=".55" />
      <path d="M25 25h32l-6 10H36v11h16v10H36v17H25z" fill="url(#fridayMetal)" filter="url(#fridayGlow)" />
      <path d="M51 39h13c9 0 15 5 15 13 0 6-3 10-9 12l10 12H66L55 61h-4V51h12c3 0 5-1 5-4s-2-4-5-4H51z" fill="url(#fridayMetal)" filter="url(#fridayGlow)" opacity=".96" />
      <circle cx="50" cy="51" r="10" fill="#061238" stroke="#48bdff" strokeWidth="1.3" />
      <circle cx="50" cy="51" r="6.2" fill="url(#fridayCore)" filter="url(#fridayGlow)" />
      <path d="M45 51h10M50 46v10" stroke="#d5f8ff" strokeWidth=".7" opacity=".8" />
      <g fill="#55c5ff"><circle cx="13" cy="48" r="1.5" /><circle cx="83" cy="48" r="1.5" /><circle cx="48" cy="13" r="1.2" /><circle cx="48" cy="83" r="1.2" /></g>
    </svg>
  )
}
