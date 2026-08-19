"use client";

import { useState, type ImgHTMLAttributes, type SyntheticEvent } from "react";

type PosterImageProps = ImgHTMLAttributes<HTMLImageElement> & {
  fallbackMode?: "poster" | "backdrop";
};

export function PosterImage({
  fallbackMode = "poster",
  onError,
  onLoad,
  className = "",
  src,
  alt = "",
  loading = "lazy",
  decoding = "async",
  ...props
}: PosterImageProps) {
  const [loaded, setLoaded] = useState(false);

  function handleError(event: SyntheticEvent<HTMLImageElement>) {
    const image = event.currentTarget;
    image.onerror = null;

    if (fallbackMode === "backdrop") {
      image.style.display = "none";
    } else {
      // If the proxy failed, try direct shikimori.io as a second attempt
      const currentSrc = image.src || "";
      if (currentSrc.includes("/alapi/shikimori/system/")) {
        const directPath = currentSrc.split("/alapi/shikimori/")[1];
        if (directPath) {
          image.src = `https://shikimori.io/${directPath}`;
          return;
        }
      }
      image.src = "/brand/anipulse-icon.png";
      image.style.objectFit = "contain";
      image.style.padding = "24%";
      image.style.background =
        "radial-gradient(circle at 50% 35%, rgba(139,92,246,.3), transparent 45%), #15151f";
    }

    onError?.(event);
  }

  function handleLoad(event: SyntheticEvent<HTMLImageElement>) {
    setLoaded(true);
    onLoad?.(event);
  }

  // eslint-disable-next-line @next/next/no-img-element
  return (
    <img
      src={src}
      alt={alt}
      loading={loading}
      decoding={decoding}
      onError={handleError}
      onLoad={handleLoad}
      className={`${className} ${loaded ? "opacity-100" : "opacity-95"} transition-opacity duration-300`}
      {...props}
    />
  );
}
