import { details } from "@/lib/catalog";
import { Player } from "./Player";

// Плеер за логином/динамикой — не индексируем, рендер на клиенте.
export const dynamic = "force-dynamic";

type Props = { params: Promise<{ id: string; ep: string }> };

export default async function WatchPage({ params }: Props) {
  const { id, ep } = await params;
  let title = "";
  try {
    const d = await details(Number(id));
    title = d.russian?.trim() || d.name;
  } catch {}
  return <Player id={Number(id)} ep={Number(ep)} title={title} />;
}
