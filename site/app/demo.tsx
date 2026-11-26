"use client";

import { useEffect, useRef } from "react";

const LOOP_START = 3.5;
const LOOP_END = 19;
const POSTER_FRAME = 13.5;

export function Demo({ src, poster }: { src: string; poster: string }) {
  const video = useRef<HTMLVideoElement>(null);

  useEffect(() => {
    const v = video.current;
    if (!v) return;
    const reduce = matchMedia("(prefers-reduced-motion: reduce)").matches;

    const start = () => {
      v.currentTime = reduce ? POSTER_FRAME : LOOP_START;
      if (!reduce) v.play().catch(() => {});
    };
    if (v.readyState >= 1) start();
    else v.addEventListener("loadedmetadata", start, { once: true });

    if (reduce) return;
    const loop = () => {
      if (v.currentTime >= LOOP_END) v.currentTime = LOOP_START;
    };
    v.addEventListener("timeupdate", loop);
    return () => v.removeEventListener("timeupdate", loop);
  }, []);

  return (
    <figure className="demo rv pop">
      <div className="blob b1" aria-hidden="true" />
      <div className="blob b2" aria-hidden="true" />
      <div className="blob b3" aria-hidden="true" />
      <video ref={video} src={src} poster={poster} width={1280} height={720} controls muted loop playsInline preload="metadata" />
      <figcaption>Plan a journey, find where to board, then walk it turn by turn.</figcaption>
    </figure>
  );
}
