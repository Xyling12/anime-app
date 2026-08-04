"use client";

import type { ImgHTMLAttributes, SyntheticEvent } from "react";

type PosterImageProps = ImgHTMLAttributes<HTMLImageElement> & {
  fallbackMode?: "poster" | "backdrop";
};

export function PosterImage({
  fallbackMode = "poster",
  onError,
  alt = "",
  ...props
}: PosterImageProps) {
  function handleError(event: SyntheticEvent<HTMLImageElement>) {
    const image = event.currentTarget;
    image.onerror = null;

    if (fallbackMode === "backdrop") {
      image.style.display = "none";
    } else {
      image.src = "/brand/anipulse-icon.png";
      image.style.objectFit = "contain";
      image.style.padding = "28%";
      image.style.background =
        "radial-gradient(circle at 50% 35%, rgba(139,92,246,.3), transparent 45%), #15151f";
    }

    onError?.(event);
  }

  // eslint-disable-next-line @next/next/no-img-element
  return <img {...props} alt={alt} onError={handleError} />;
}
