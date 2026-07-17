export type WorkspaceId =
  | 'CORE'
  | 'CHAT'
  | 'ANALYSIS'
  | 'RESEARCH'
  | 'NEWS'
  | 'IMAGE'
  | 'NAVIGATION'
  | 'GMAIL'
  | 'WHATSAPP'
  | 'DIARY'
  | 'MEMORY'
  | 'VOICE_IDENTITY'
  | 'VISION'
  | 'DIAGNOSTICS'

export interface WorkspaceSpec {
  id: WorkspaceId
  label: string
  subtitle: string
  responseLabel: string
  loadingClass: 'light' | 'medium' | 'heavy'
}

export const WORKSPACES: Record<WorkspaceId, WorkspaceSpec> = {
  CORE: { id:'CORE', label:'CORE HUD', subtitle:'GENERAL INTELLIGENCE', responseLabel:'F.R.I.D.A.Y. RESPONSE', loadingClass:'light' },
  CHAT: { id:'CHAT', label:'CHAT CHAMBER', subtitle:'CONTINUOUS CONVERSATION', responseLabel:'ACTIVE DIALOGUE', loadingClass:'light' },
  ANALYSIS: { id:'ANALYSIS', label:'ANALYSIS CHAMBER', subtitle:'STRUCTURAL AND TECHNICAL RECONSTRUCTION', responseLabel:'ANALYSIS REPORT', loadingClass:'heavy' },
  RESEARCH: { id:'RESEARCH', label:'RESEARCH WORKSPACE', subtitle:'EVIDENCE AND SOURCE SYNTHESIS', responseLabel:'RESEARCH BRIEF', loadingClass:'medium' },
  NEWS: { id:'NEWS', label:'NEWS INTELLIGENCE', subtitle:'VERIFIED LIVE CONTEXT', responseLabel:'NEWS BRIEFING', loadingClass:'medium' },
  IMAGE: { id:'IMAGE', label:'IMAGE STUDIO', subtitle:'VISUAL SYNTHESIS', responseLabel:'GENERATION STATUS', loadingClass:'heavy' },
  NAVIGATION: { id:'NAVIGATION', label:'NAVIGATION', subtitle:'ROUTE AND SPATIAL INTELLIGENCE', responseLabel:'ROUTE BRIEF', loadingClass:'heavy' },
  GMAIL: { id:'GMAIL', label:'GMAIL WORKSPACE', subtitle:'OWNER-AUTHORIZED MAIL', responseLabel:'MAIL OPERATION', loadingClass:'medium' },
  WHATSAPP: { id:'WHATSAPP', label:'WHATSAPP WORKSPACE', subtitle:'CONFIRMED MESSAGING', responseLabel:'MESSAGE OPERATION', loadingClass:'medium' },
  DIARY: { id:'DIARY', label:'PRIVATE DIARY', subtitle:'ENCRYPTED OWNER VAULT', responseLabel:'VAULT STATUS', loadingClass:'light' },
  MEMORY: { id:'MEMORY', label:'MEMORY VAULT', subtitle:'OWNER-CONTROLLED CONTINUITY', responseLabel:'MEMORY OPERATION', loadingClass:'medium' },
  VOICE_IDENTITY: { id:'VOICE_IDENTITY', label:'OWNER VOICE LAB', subtitle:'ACOUSTIC FAMILIARITY', responseLabel:'VOICE PROFILE STATUS', loadingClass:'medium' },
  VISION: { id:'VISION', label:'OPTICAL VISION', subtitle:'CAMERA AND SCREEN INTELLIGENCE', responseLabel:'VISUAL ANALYSIS', loadingClass:'heavy' },
  DIAGNOSTICS: { id:'DIAGNOSTICS', label:'DIAGNOSTICS VAULT', subtitle:'INTERNAL ENGINE EVENTS', responseLabel:'SYSTEM DIAGNOSTICS', loadingClass:'medium' },
}

export function workspaceFromText(raw: string, fallback: WorkspaceId = 'CORE'): WorkspaceId {
  const text = raw.toLowerCase().replace(/\s+/g, ' ').trim()
  if (!text) return fallback
  if (/private diary|diary vault|journal|secure notes/.test(text)) return 'DIARY'
  if (/memory vault|memory queue|saved memories|conversation archive/.test(text)) return 'MEMORY'
  if (/owner voice|voice identity|voice profile|voice lab|learn my voice/.test(text)) return 'VOICE_IDENTITY'
  if (/gmail|email|mailbox|inbox/.test(text)) return 'GMAIL'
  if (/whatsapp|waapi/.test(text)) return 'WHATSAPP'
  if (/(create|generate|render|image synthesis|image studio|poster|wallpaper)/.test(text) && /(image|picture|photo|poster|wallpaper|logo|art)/.test(text)) return 'IMAGE'
  if (/news|headline|broadcast|live coverage|what is happening/.test(text)) return 'NEWS'
  if (/research|investigate|sources|evidence|deep dive/.test(text)) return 'RESEARCH'
  if (/analy[sz]e|analysis|reconstruct|exploded view|components/.test(text)) return 'ANALYSIS'
  if (/route|navigate|directions|map|distance from|travel from/.test(text)) return 'NAVIGATION'
  if (/open camera|optical|screen share|see my screen|vision|what is this/.test(text)) return 'VISION'
  if (/diagnostic|system log|debug vault|engine events/.test(text)) return 'DIAGNOSTICS'
  if (/i want to chat|chat with me|let us talk|let's talk|conversation/.test(text)) return 'CHAT'
  return fallback
}
