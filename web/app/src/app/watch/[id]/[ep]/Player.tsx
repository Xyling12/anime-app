"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Hls from "hls.js";
import { kodikDubs, kodikStream, skipTimes } from "@/lib/video";
import { getProgress, saveProgress } from "@/lib/progress";

type Skip = { start: number; stop: number };

export function Player({ id, ep, title }: { id: number; ep: number; title: string }) {
  const router = useRouter();
  const params = useSearchParams();
  const videoRef = useRef<HTMLVideoElement>(null);
  const hlsRef = useRef<Hls | null>(null);
  const recapSkippedRef = useRef(false);
  const switchPositionRef = useRef(0);
  const hideTimerRef = useRef<NodeJS.Timeout | null>(null);
  const isHoveringControlsRef = useRef(false);
  const isSelectFocusedRef = useRef(false);

  const [dubs, setDubs] = useState<{ title: string; link: string }[]>([]);
  const [dubIdx, setDubIdx] = useState(Number(params.get("dub") || 0));
  const [qualities, setQualities] = useState<Record<string, string>>({});
  const [quality, setQuality] = useState<string>("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [skip, setSkip] = useState<{ op?: Skip; ed?: Skip }>({});
  const [pos, setPos] = useState(0);
  const [duration, setDuration] = useState(0);
  const [playing, setPlaying] = useState(false);
  const [buffering, setBuffering] = useState(false);
  const [volume, setVolume] = useState(1);
  const [autoSkipRecap, setAutoSkipRecap] = useState(true);
  const [controlsVisible, setControlsVisible] = useState(true);

  const clearIdleTimer = () => {
    if (hideTimerRef.current) {
      clearTimeout(hideTimerRef.current);
      hideTimerRef.current = null;
    }
  };

  const showControls = () => {
    setControlsVisible(true);
    clearIdleTimer();
    if (
      playing &&
      !isHoveringControlsRef.current &&
      !isSelectFocusedRef.current &&
      !loading &&
      !error &&
      !buffering
    ) {
      hideTimerRef.current = setTimeout(() => {
        setControlsVisible(false);
      }, 2600);
    }
  };

  useEffect(() => {
    if (!playing || loading || error || buffering) {
      clearIdleTimer();
      setControlsVisible(true);
    } else {
      showControls();
    }
    return () => clearIdleTimer();
  }, [playing, loading, error, buffering]);

  const handleUserActivity = () => {
    showControls();
  };

  const handleMouseLeave = () => {
    if (playing && !isSelectFocusedRef.current && !loading && !error && !buffering) {
      clearIdleTimer();
      hideTimerRef.current = setTimeout(() => {
        setControlsVisible(false);
      }, 500);
    }
  };

  const handleControlsMouseEnter = () => {
    isHoveringControlsRef.current = true;
    clearIdleTimer();
    setControlsVisible(true);
  };

  const handleControlsMouseLeave = () => {
    isHoveringControlsRef.current = false;
    showControls();
  };

  const handleSelectFocus = () => {
    isSelectFocusedRef.current = true;
    clearIdleTimer();
    setControlsVisible(true);
  };

  const handleSelectBlur = () => {
    isSelectFocusedRef.current = false;
    showControls();
  };

  useEffect(() => {
    const stored = localStorage.getItem("anipulse_auto_skip_recap");
    if (stored === null) localStorage.setItem("anipulse_auto_skip_recap", "1");
    else setAutoSkipRecap(stored === "1");
  }, []);

  useEffect(() => {
    recapSkippedRef.current = false;
  }, [id, ep]);

  // 1. Озвучки
  useEffect(() => {
    kodikDubs(id).then(setDubs).catch(() => setError("Не удалось получить озвучки"));
    skipTimes(id, ep).then(setSkip).catch(() => {});
  }, [id, ep]);

  // 2. Извлечение m3u8 для выбранной озвучки/серии
  useEffect(() => {
    const dub = dubs[dubIdx];
    if (!dub) return;
    setLoading(true);
    setError(null);
    const cacheKey = `anipulse_stream:${id}:${ep}:${dub.link}`;
    const cached = sessionStorage.getItem(cacheKey);
    const request = cached
      ? Promise.resolve(JSON.parse(cached) as Record<string, string>)
      : kodikStream(dub.link, ep).then((result) => {
          sessionStorage.setItem(cacheKey, JSON.stringify(result));
          return result;
        });
    request
      .then((q) => {
        const keys = Object.keys(q).map(Number).filter((n) => n > 0);
        if (!keys.length) throw new Error("empty");
        const best = String(Math.min(720, Math.max(...keys)));
        setQualities(q);
        setQuality(q[best] ? best : String(Math.max(...keys)));
      })
      .catch(() => setError("Видео не найдено. Попробуй другую озвучку."))
      .finally(() => setLoading(false));
  }, [dubs, dubIdx, ep]);

  const src = quality ? qualities[quality] : "";

  // 3. hls.js → <video>, сегменты напрямую с CDN (CORS *)
  useEffect(() => {
    const video = videoRef.current;
    if (!video || !src) return;
    const saved = getProgress(id);
    const resumeAt = switchPositionRef.current || (saved?.episode === ep ? saved.positionSec : 0);
    switchPositionRef.current = 0;

    if (video.canPlayType("application/vnd.apple.mpegurl")) {
      video.src = src;
      if (resumeAt > 5) video.currentTime = resumeAt;
    } else if (Hls.isSupported()) {
      const hls = new Hls({
        enableWorker: true,
        startLevel: -1,
        capLevelToPlayerSize: true,
        maxBufferLength: 30,
        maxMaxBufferLength: 60,
        backBufferLength: 30,
        manifestLoadingTimeOut: 10000,
        fragLoadingTimeOut: 20000,
      });
      hlsRef.current = hls;
      hls.loadSource(src);
      hls.attachMedia(video);
      hls.on(Hls.Events.MANIFEST_PARSED, () => {
        if (resumeAt > 5) video.currentTime = resumeAt;
        video.play().catch(() => {});
      });
      hls.on(Hls.Events.ERROR, (_e, data) => {
        if (!data.fatal) return;
        if (data.type === Hls.ErrorTypes.NETWORK_ERROR) hls.startLoad();
        else if (data.type === Hls.ErrorTypes.MEDIA_ERROR) hls.recoverMediaError();
        else setError("Ошибка воспроизведения. Смени качество или озвучку.");
      });
      return () => hls.destroy();
    }
  }, [src, id, ep]);

  // 4. Сохранение прогресса каждые 5с
  useEffect(() => {
    const t = setInterval(() => {
      const v = videoRef.current;
      if (v && !v.paused && v.currentTime > 0) {
        setPos(v.currentTime);
        saveProgress(id, { episode: ep, positionSec: v.currentTime, dubIdx, updatedAt: Date.now() });
      }
    }, 5000);
    return () => clearInterval(t);
  }, [id, ep, dubIdx]);

  const qualityKeys = useMemo(
    () => Object.keys(qualities).map(Number).filter((n) => n > 0).sort((a, b) => b - a),
    [qualities],
  );

  const showSkipOp = skip.op && pos >= skip.op.start && pos <= skip.op.stop;
  const showSkipEd = skip.ed && pos >= skip.ed.start && pos <= skip.ed.stop;
  const formatTime = (seconds: number) => {
    if (!Number.isFinite(seconds)) return "0:00";
    const mins = Math.floor(seconds / 60);
    return `${mins}:${Math.floor(seconds % 60).toString().padStart(2, "0")}`;
  };

  const togglePlay = () => {
    const video = videoRef.current;
    if (!video) return;
    if (video.paused) video.play().catch(() => {});
    else video.pause();
  };

  const toggleFullscreen = () => {
    const root = document.querySelector(".watch-player");
    if (!document.fullscreenElement) root?.requestFullscreen().catch(() => {});
    else document.exitFullscreen().catch(() => {});
  };

  const seekTo = (target: number) => {
    const video = videoRef.current;
    if (!video) return;
    const safeTarget = Math.max(0, Math.min(video.duration || target, target));
    hlsRef.current?.startLoad(safeTarget);
    video.currentTime = safeTarget;
    video.play().catch(() => {});
  };

  const trackPlayback = (video: HTMLVideoElement) => {
    setPos(video.currentTime);
    const recapEnd = skip.op?.start;
    if (
      autoSkipRecap &&
      !recapSkippedRef.current &&
      video.duration > 200 &&
      video.currentTime >= 3 &&
      video.currentTime <= 8 &&
      recapEnd !== undefined &&
      recapEnd >= 30 &&
      recapEnd <= 150
    ) {
      recapSkippedRef.current = true;
      seekTo(recapEnd);
    }
  };

  // Keyboard shortcuts
  useEffect(() => {
    const handleGlobalKeyDown = (e: KeyboardEvent) => {
      if (
        e.target instanceof HTMLInputElement ||
        e.target instanceof HTMLSelectElement ||
        e.target instanceof HTMLTextAreaElement
      ) {
        return;
      }
      showControls();
      if (e.key === " " || e.key === "k" || e.key === "K" || e.key === "л" || e.key === "Л") {
        e.preventDefault();
        togglePlay();
      } else if (e.key === "ArrowLeft") {
        e.preventDefault();
        const v = videoRef.current;
        if (v) seekTo(v.currentTime - 5);
      } else if (e.key === "ArrowRight") {
        e.preventDefault();
        const v = videoRef.current;
        if (v) seekTo(v.currentTime + 5);
      } else if (e.key === "ArrowUp") {
        e.preventDefault();
        const v = videoRef.current;
        if (v) {
          const next = Math.min(1, Number((v.volume + 0.1).toFixed(2)));
          v.volume = next;
          setVolume(next);
        }
      } else if (e.key === "ArrowDown") {
        e.preventDefault();
        const v = videoRef.current;
        if (v) {
          const next = Math.max(0, Number((v.volume - 0.1).toFixed(2)));
          v.volume = next;
          setVolume(next);
        }
      } else if (e.key === "f" || e.key === "F" || e.key === "а" || e.key === "А") {
        e.preventDefault();
        toggleFullscreen();
      } else if (e.key === "m" || e.key === "M" || e.key === "ь" || e.key === "Ь") {
        e.preventDefault();
        const v = videoRef.current;
        if (v) {
          const next = v.volume > 0 ? 0 : 1;
          v.volume = next;
          setVolume(next);
        }
      }
    };
    window.addEventListener("keydown", handleGlobalKeyDown);
    return () => window.removeEventListener("keydown", handleGlobalKeyDown);
  }, [playing, autoSkipRecap]);

  const handleStageClick = () => {
    if (!controlsVisible) {
      showControls();
      return;
    }
    togglePlay();
  };

  const isIdle = !controlsVisible && playing && !loading && !buffering && !error;

  return (
    <div
      className={`watch-player fixed inset-0 z-50 flex flex-col bg-black ${isIdle ? "is-idle" : ""}`}
      onMouseMove={handleUserActivity}
      onPointerMove={handleUserActivity}
      onTouchStart={handleUserActivity}
      onMouseLeave={handleMouseLeave}
    >
      {/* Шапка (Toolbar с озвучкой и качеством) */}
      <div
        className="player-toolbar text-white"
        onMouseEnter={handleControlsMouseEnter}
        onMouseLeave={handleControlsMouseLeave}
      >
        <button onClick={() => router.back()} aria-label="Назад" className="player-icon-button">
          ←
        </button>
        <img src="/brand/anipulse-icon.png" alt="" className="h-8 w-8 rounded-[10px]" />
        <span className="min-w-0 truncate text-sm font-semibold">
          <b>{title}</b>
          <small className="ml-2 text-white/45">Серия {ep}</small>
        </span>
        <div className="ml-auto flex items-center gap-2">
          {qualityKeys.length > 1 && (
            <select
              value={quality}
              onFocus={handleSelectFocus}
              onBlur={handleSelectBlur}
              onChange={(e) => {
                switchPositionRef.current = videoRef.current?.currentTime || 0;
                setQuality(e.target.value);
              }}
              className="player-select rounded px-3 py-2 text-sm"
            >
              {qualityKeys.map((q) => (
                <option key={q} value={String(q)}>
                  {q}p
                </option>
              ))}
            </select>
          )}
          {dubs.length > 1 && (
            <select
              value={dubIdx}
              onFocus={handleSelectFocus}
              onBlur={handleSelectBlur}
              onChange={(e) => {
                switchPositionRef.current = videoRef.current?.currentTime || 0;
                setDubIdx(Number(e.target.value));
              }}
              className="player-select max-w-[240px] rounded px-3 py-2 text-sm"
            >
              {dubs.map((d, i) => (
                <option key={i} value={i}>
                  {d.title}
                </option>
              ))}
            </select>
          )}
        </div>
      </div>

      {/* Видео сцена */}
      <div className="player-stage">
        {loading && (
          <div className="player-loader absolute z-20">
            <i />
            <span>Готовим серию</span>
            <small>Подключаем видеопоток…</small>
          </div>
        )}
        {error && (
          <div className="absolute z-20 flex flex-col items-center gap-3 text-white">
            <p>{error}</p>
            <button onClick={() => router.back()} className="rounded bg-white/20 px-4 py-2">
              Назад
            </button>
          </div>
        )}
        <video
          ref={videoRef}
          className="absolute inset-0 h-full w-full object-contain cursor-pointer"
          playsInline
          onClick={handleStageClick}
          onDoubleClick={toggleFullscreen}
          onPlay={() => setPlaying(true)}
          onPause={() => setPlaying(false)}
          onWaiting={() => setBuffering(true)}
          onSeeking={() => setBuffering(true)}
          onPlaying={() => setBuffering(false)}
          onCanPlay={() => setBuffering(false)}
          onLoadedMetadata={(e) => setDuration(e.currentTarget.duration || 0)}
          onTimeUpdate={(e) => trackPlayback(e.currentTarget)}
          preload="auto"
          onVolumeChange={(e) => setVolume(e.currentTarget.volume)}
        />

        {!loading && !error && !playing && (
          <button onClick={togglePlay} className="player-big-play" aria-label="Воспроизвести">
            <span className="player-play-shape" />
          </button>
        )}
        {!loading && !error && buffering && playing && (
          <div className="player-buffering" aria-label="Буферизация">
            <i />
          </div>
        )}

        {showSkipOp && (
          <button
            onClick={() => {
              if (skip.op) seekTo(skip.op.stop);
            }}
            className="player-skip-button"
          >
            <span>Опенинг</span>Пропустить <i className="player-skip-shape" />
          </button>
        )}
        {showSkipEd && (
          <button
            onClick={() => {
              if (skip.ed) seekTo(skip.ed.stop);
            }}
            className="player-skip-button"
          >
            <span>Эндинг</span>Пропустить <i className="player-skip-shape" />
          </button>
        )}
        <div
          className="player-controls"
          onMouseEnter={handleControlsMouseEnter}
          onMouseLeave={handleControlsMouseLeave}
        >
          <div className="player-timeline-markers" aria-hidden="true">
            {skip.op && duration > 0 && (
              <i
                className="is-opening-end"
                style={{ left: `${Math.min(100, (skip.op.stop / duration) * 100)}%` }}
                title="Конец опенинга"
              />
            )}
            {skip.ed && duration > 0 && (
              <>
                <i
                  className="is-ending-start"
                  style={{ left: `${Math.min(100, (skip.ed.start / duration) * 100)}%` }}
                  title="Начало эндинга"
                />
                <i
                  className="is-ending-end"
                  style={{ left: `${Math.min(100, (skip.ed.stop / duration) * 100)}%` }}
                  title="Конец эндинга"
                />
              </>
            )}
          </div>
          <input
            aria-label="Прогресс просмотра"
            type="range"
            min="0"
            max={duration || 0}
            step="0.1"
            value={Math.min(pos, duration || 0)}
            onChange={(e) => {
              const next = Number(e.target.value);
              seekTo(next);
              setPos(next);
            }}
            style={{ "--progress": `${duration ? (pos / duration) * 100 : 0}%` } as React.CSSProperties}
            className="player-progress"
          />
          <div className="player-controls-row">
            <button
              onClick={togglePlay}
              className="player-icon-button"
              aria-label={playing ? "Пауза" : "Воспроизвести"}
            >
              {playing ? <span className="player-pause-shape" /> : <span className="player-play-shape player-play-shape-small" />}
            </button>
            <span className="player-time">
              {formatTime(pos)} <i>/</i> {formatTime(duration)}
            </span>
            <label className="player-volume">
              <span className={volume === 0 ? "player-speaker is-muted" : "player-speaker"} />
              <input
                aria-label="Громкость"
                type="range"
                min="0"
                max="1"
                step=".05"
                value={volume}
                onChange={(e) => {
                  if (videoRef.current) {
                    const v = Number(e.target.value);
                    videoRef.current.volume = v;
                    setVolume(v);
                  }
                }}
              />
            </label>
            <div className="player-episode-nav">
              <button disabled={ep <= 1} onClick={() => router.push(`/watch/${id}/${ep - 1}?dub=${dubIdx}`)}>
                ← Предыдущая
              </button>
              <span>{ep} серия</span>
              <button onClick={() => router.push(`/watch/${id}/${ep + 1}?dub=${dubIdx}`)}>
                Следующая →
              </button>
            </div>
            <button onClick={toggleFullscreen} className="player-icon-button" aria-label="Полный экран">
              <span className="player-fullscreen-shape" />
            </button>
            <button
              onClick={() => {
                const next = !autoSkipRecap;
                setAutoSkipRecap(next);
                localStorage.setItem("anipulse_auto_skip_recap", next ? "1" : "0");
              }}
              className={`player-auto-skip ${autoSkipRecap ? "is-on" : ""}`}
              title="Автоматически пропускать повтор в начале серии"
            >
              Повтор {autoSkipRecap ? "вкл" : "выкл"}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
