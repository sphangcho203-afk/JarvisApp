import { Suspense, useEffect, useMemo, useRef } from 'react'
import { Canvas, useFrame } from '@react-three/fiber'
import { Bloom, EffectComposer, Vignette } from '@react-three/postprocessing'
import * as THREE from 'three'
import type { AudioMetricsRef, HelixState, ThemeSpec } from './types'

interface Props { mode: HelixState; theme: ThemeSpec; audio: AudioMetricsRef }

const vertexShader = `
uniform float uTime;
uniform float uAudio;
varying vec3 vNormal;
varying float vPulse;
void main() {
  vNormal = normalize(normalMatrix * normal);
  float wave = sin(position.y * 7.0 + uTime * 2.2) * 0.035;
  float ripple = sin(length(position.xz) * 12.0 - uTime * 3.0) * 0.025;
  vec3 displaced = position + normal * (wave + ripple) * (0.65 + uAudio * 2.2);
  vPulse = 0.5 + 0.5 * sin(uTime * 2.5 + position.y * 5.0);
  gl_Position = projectionMatrix * modelViewMatrix * vec4(displaced, 1.0);
}`

const fragmentShader = `
uniform vec3 uColor;
uniform float uAudio;
varying vec3 vNormal;
varying float vPulse;
void main() {
  float fresnel = pow(1.0 - abs(dot(vNormal, vec3(0.0, 0.0, 1.0))), 2.1);
  float intensity = 0.35 + fresnel * 2.8 + vPulse * 0.3 + uAudio * 1.25;
  gl_FragColor = vec4(uColor * intensity, min(1.0, 0.42 + fresnel + uAudio * 0.28));
}`

export function HelixCanvas({ mode, theme, audio }: Props) {
  return (
    <Canvas
      dpr={[1, 1.55]}
      camera={{ position: [0, 0, 7.35], fov: 42, near: .1, far: 45 }}
      gl={{ antialias: true, alpha: true, powerPreference: 'high-performance' }}
      onCreated={({ gl }) => {
        gl.outputColorSpace = THREE.SRGBColorSpace
        gl.toneMapping = THREE.ACESFilmicToneMapping
        gl.toneMappingExposure = 1.1
      }}
    >
      <color attach="background" args={['#02060a']} />
      <fog attach="fog" args={['#02060a', 8, 22]} />
      <ambientLight intensity={.08} />
      <directionalLight position={[3, 4, 5]} color={theme.hex} intensity={.65} />
      <Suspense fallback={null}>
        <Core mode={mode} theme={theme} audio={audio} />
        <ParticleField mode={mode} theme={theme} audio={audio} />
      </Suspense>
      <EffectComposer multisampling={0}>
        <Bloom mipmapBlur intensity={1.42} luminanceThreshold={.18} luminanceSmoothing={.7} />
        <Vignette eskil={false} offset={.2} darkness={.75} />
      </EffectComposer>
    </Canvas>
  )
}

function Core({ mode, theme, audio }: Props) {
  const group = useRef<THREE.Group>(null)
  const shell = useRef<THREE.Mesh>(null)
  const material = useMemo(() => new THREE.ShaderMaterial({
    uniforms: { uTime: { value: 0 }, uAudio: { value: 0 }, uColor: { value: new THREE.Color(theme.hex) } },
    vertexShader,
    fragmentShader,
    transparent: true,
    depthWrite: false,
    blending: THREE.AdditiveBlending,
    toneMapped: false,
  }), [])
  const target = useMemo(() => new THREE.Color(theme.hex), [theme.hex])
  const bright = useMemo(() => new THREE.Color(theme.hex).multiplyScalar(3.5), [theme.hex])
  useEffect(() => () => material.dispose(), [material])

  useFrame((state, delta) => {
    if (!group.current || !shell.current) return
    const m = audio.current
    const energy = m.rms * 1.2 + m.bass * .45 + theme.energy * .16
    group.current.rotation.y += delta * (.12 + energy * .54) * theme.speed
    group.current.rotation.x = THREE.MathUtils.damp(group.current.rotation.x, state.pointer.y * .12, 3, delta)
    group.current.rotation.z = THREE.MathUtils.damp(group.current.rotation.z, -state.pointer.x * .07, 3, delta)
    group.current.scale.setScalar(THREE.MathUtils.damp(group.current.scale.x, 1 + m.peak * .14 + theme.energy * .025, 6, delta))
    shell.current.rotation.x -= delta * .21 * theme.speed
    shell.current.rotation.y += delta * .29 * theme.speed
    material.uniforms.uTime.value = state.clock.elapsedTime
    material.uniforms.uAudio.value = THREE.MathUtils.damp(material.uniforms.uAudio.value, energy, 8, delta)
    material.uniforms.uColor.value.lerp(target, 1 - Math.exp(-delta * 7))
  })

  return (
    <group ref={group}>
      <pointLight color={theme.hex} intensity={22} distance={8} decay={2} />
      <mesh material={material}><icosahedronGeometry args={[1.02, 6]} /></mesh>
      <mesh ref={shell} scale={1.2}>
        <icosahedronGeometry args={[1.02, 2]} />
        <meshBasicMaterial color={bright} wireframe transparent opacity={.12} blending={THREE.AdditiveBlending} toneMapped={false} />
      </mesh>
      <mesh scale={.46}>
        <sphereGeometry args={[1, 40, 40]} />
        <meshBasicMaterial color={bright} transparent opacity={.72} blending={THREE.AdditiveBlending} toneMapped={false} />
      </mesh>
      <OrbitRings theme={theme} audio={audio} />
      <DataNodes theme={theme} audio={audio} />
    </group>
  )
}

function OrbitRings({ theme, audio }: { theme: ThemeSpec; audio: AudioMetricsRef }) {
  const refs = [useRef<THREE.Group>(null), useRef<THREE.Group>(null), useRef<THREE.Group>(null), useRef<THREE.Group>(null)]
  useFrame((_, delta) => refs.forEach((ref, index) => {
    if (!ref.current) return
    const velocity = (.12 + audio.current.rms * .9) * theme.speed * (index % 2 === 0 ? 1 : -1)
    ref.current.rotation.x += delta * velocity * (.28 + index * .08)
    ref.current.rotation.y += delta * velocity * (.4 + index * .12)
    const scale = 1 + audio.current.peak * (.025 + index * .012)
    ref.current.scale.setScalar(THREE.MathUtils.damp(ref.current.scale.x, scale, 8, delta))
  }))
  const rings = [
    { radius: 1.52, tube: .012, rotation: [.35, .2, 0] as [number, number, number] },
    { radius: 1.82, tube: .009, rotation: [1.12, .05, .35] as [number, number, number] },
    { radius: 2.12, tube: .007, rotation: [.25, 1.2, .62] as [number, number, number] },
    { radius: 2.42, tube: .006, rotation: [1.45, .65, .1] as [number, number, number] },
  ]
  return <>{rings.map((ring, index) => (
    <group key={ring.radius} ref={refs[index]} rotation={ring.rotation}>
      <mesh><torusGeometry args={[ring.radius, ring.tube, 8, 180]} /><meshBasicMaterial color={theme.hex} transparent opacity={.54 - index * .08} blending={THREE.AdditiveBlending} toneMapped={false} /></mesh>
      <mesh rotation={[0, 0, Math.PI / (3 + index)]}><torusGeometry args={[ring.radius, ring.tube * .35, 6, 120]} /><meshBasicMaterial color="#ffffff" transparent opacity={.2} blending={THREE.AdditiveBlending} toneMapped={false} /></mesh>
    </group>
  ))}</>
}

function DataNodes({ theme, audio }: { theme: ThemeSpec; audio: AudioMetricsRef }) {
  const root = useRef<THREE.Group>(null)
  const nodes = useMemo(() => Array.from({ length: 8 }, (_, index) => {
    const angle = index / 8 * Math.PI * 2
    const radius = 2.68 + (index % 2) * .18
    return [Math.cos(angle) * radius, Math.sin(angle * 1.5) * .42, Math.sin(angle) * radius] as [number, number, number]
  }), [])
  useFrame((_, delta) => { if (root.current) root.current.rotation.y -= delta * (.04 + audio.current.mid * .18) })
  return <group ref={root}>{nodes.map((position, index) => (
    <group key={index} position={position}>
      <mesh scale={.06 + audio.current.peak * .025}><sphereGeometry args={[1, 16, 16]} /><meshBasicMaterial color={theme.hex} toneMapped={false} /></mesh>
      <mesh scale={.13}><ringGeometry args={[.55, .72, 24]} /><meshBasicMaterial color={theme.hex} transparent opacity={.45} side={THREE.DoubleSide} blending={THREE.AdditiveBlending} toneMapped={false} /></mesh>
    </group>
  ))}</group>
}

function ParticleField({ mode, theme, audio }: Props) {
  const points = useRef<THREE.Points>(null)
  const positions = useMemo(() => {
    const data = new Float32Array(760 * 3)
    for (let i = 0; i < 760; i++) {
      const radius = 4 + Math.random() * 7
      const theta = Math.random() * Math.PI * 2
      const phi = Math.acos(2 * Math.random() - 1)
      data[i * 3] = radius * Math.sin(phi) * Math.cos(theta)
      data[i * 3 + 1] = radius * Math.cos(phi) * .7
      data[i * 3 + 2] = radius * Math.sin(phi) * Math.sin(theta)
    }
    return data
  }, [])
  useFrame((_, delta) => {
    if (!points.current) return
    const direction = mode === 'ERROR' ? -1 : 1
    points.current.rotation.y += delta * direction * (.018 + audio.current.treble * .22) * theme.speed
    points.current.rotation.x += delta * .006
  })
  return <points ref={points}>
    <bufferGeometry><bufferAttribute attach="attributes-position" args={[positions, 3]} /></bufferGeometry>
    <pointsMaterial color={theme.hex} size={.028} transparent opacity={.46} depthWrite={false} blending={THREE.AdditiveBlending} toneMapped={false} sizeAttenuation />
  </points>
}
