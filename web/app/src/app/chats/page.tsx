import Link from "next/link";
import { Icon, type IconName } from "@/components/Icon";

export const metadata = { title: "Сообщество — AniPulse" };
const CARDS: { href:string; icon:IconName; title:string; sub:string; meta:string }[] = [
  { href:"/chat", icon:"message", title:"Общий чат", sub:"Обсуждай новинки со всем сообществом", meta:"Общий канал" },
  { href:"/dm", icon:"mail", title:"Личные сообщения", sub:"Продолжай личные диалоги", meta:"Только для тебя" },
  { href:"/friends", icon:"users", title:"Друзья", sub:"Список друзей, заявки и статусы", meta:"Твои контакты" },
  { href:"/notifications", icon:"bell", title:"Уведомления", sub:"Упоминания, ответы и новые сообщения", meta:"Центр событий" },
];
export default function ChatsPage(){return <div className="page-shell"><div className="mb-9 max-w-2xl"><div className="eyebrow mb-2">AniPulse Social</div><h1 className="text-4xl font-black tracking-[-.045em] md:text-5xl">Сообщество</h1><p className="mt-3 text-lg leading-7 text-text-muted">Общайся, находи друзей и обсуждай любимые тайтлы — теперь в полноценном веб-пространстве.</p></div><div className="grid gap-4 md:grid-cols-2">{CARDS.map(c=><Link key={c.href} href={c.href} className="panel group flex min-h-44 flex-col justify-between p-6 transition hover:-translate-y-1 hover:border-primary/30"><div className="flex items-start justify-between"><span className="grid h-14 w-14 place-items-center rounded-2xl bg-primary/10 text-primary"><Icon name={c.icon} className="h-6 w-6"/></span><Icon name="arrow" className="h-5 w-5 text-text-dim transition group-hover:translate-x-1 group-hover:text-primary"/></div><div className="mt-6"><span className="text-xs font-bold uppercase tracking-wider text-text-dim">{c.meta}</span><h2 className="mt-1 text-xl font-bold">{c.title}</h2><p className="mt-1 text-sm text-text-muted">{c.sub}</p></div></Link>)}</div></div>}
