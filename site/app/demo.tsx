"use client";

import { useEffect, useRef } from "react";

export function Demo({ src, poster }: { src: string; poster: string }) {
  const video = useRef<HTMLVideoElement>(null);

  useEffect(() => {
    if (!matchMedia("(prefers-reduced-motion: reduce)").matches) video.current?.play().catch(() => {});
  }, []);

  return (
    <figure className="demo">
      <video ref={video} src={src} poster={poster} width={1280} height={720} controls muted loop playsInline preload="metadata" />
      <figcaption>Plan a journey, find where to board, then walk it turn by turn.</figcaption>
    </figure>
  );
}
