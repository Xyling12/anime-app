"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { friends, friendAction, type UserCard } from "@/lib/social";
import { useMe } from "@/lib/useMe";
import { Avatar } from "@/components/Avatar";
import f from "@/components/Friends.module.css";

export default function FriendsPage() {
  const { me, loading } = useMe();
  const [data, setData] = useState<{friends:UserCard[];incoming:UserCard[]}>({friends:[],incoming:[]});
  const [query,setQuery] = useState("");
  const [busy,setBusy] = useState("");
  const [error,setError] = useState("");

  function reload() { friends().then(setData).catch(() => setError("Не удалось загрузить друзей.")); }
  useEffect(() => { if (me) reload(); }, [me]);
  async function act(action:"accept"|"decline"|"remove",nick:string) {
    setBusy(`${action}:${nick}`); setError("");
    try { await friendAction(action,nick); reload(); }
    catch { setError("Не удалось выполнить действие. Попробуйте ещё раз."); }
    finally { setBusy(""); }
  }
  const visible = useMemo(()=>data.friends.filter(user=>user.nick.toLowerCase().includes(query.trim().toLowerCase())),[data.friends,query]);

  if (loading) return <div className="page-shell"><div className="skeleton h-52 rounded-3xl"/><div className="mt-6 grid gap-3 md:grid-cols-3">{[1,2,3].map(x=><div key={x} className="skeleton h-64 rounded-2xl"/>)}</div></div>;
  if (!me) return <div className="page-shell"><div className="panel mx-auto max-w-lg p-10 text-center"><h1 className="text-3xl font-black">Друзья рядом с аккаунтом</h1><p className="mt-3 text-text-muted">Войдите, чтобы принимать заявки, писать друзьям и открывать их профили.</p><Link href="/profile" className="cinema-primary mt-6">Войти в AniPulse</Link></div></div>;

  return <div className={f.page}>
    <header className={f.hero}><div><p className="eyebrow">Ваш круг AniPulse</p><h1>Друзья</h1><p>Находите людей с похожим вкусом, делитесь тайтлами и возвращайтесь к обсуждениям вместе.</p></div><div className={f.heroPeople}>{data.friends.slice(0,4).map(user=><div key={user.nick} className="-ml-3 overflow-hidden rounded-full border-4 border-surface shadow-xl"><Avatar id={user.avatar} size={48} nick={user.nick}/></div>)}<span>＋</span></div></header>
    {error && <div className="mx-auto mt-4 w-[min(100%-40px,1180px)] rounded-xl border border-red-500/30 bg-red-500/10 p-3 text-sm text-red-200">{error}</div>}
    {data.incoming.length>0 && <section className={f.requests}><div className={f.sectionTitle}><span><small>НОВЫЕ ЗНАКОМСТВА</small><h2>Входящие заявки</h2></span><em>{data.incoming.length} ожидают ответа</em></div><div className={f.requestGrid}>{data.incoming.map(user=><article key={user.nick}><Avatar id={user.avatar} size={44} nick={user.nick}/><span><b>{user.nick}</b><small>{user.favoriteGenre || "Пользователь AniPulse"}</small><em>{user.stats?.favoritesCount ? `${user.stats.favoritesCount} тайтлов в коллекции` : "Хочет добавить вас в друзья"}</em></span><div><button disabled={!!busy} onClick={()=>act("accept",user.nick)}>Принять</button><button disabled={!!busy} onClick={()=>act("decline",user.nick)} aria-label={`Отклонить заявку ${user.nick}`}>×</button></div></article>)}</div></section>}
    <section className={f.friends}><div className={f.friendsHead}><div><p className="eyebrow">Ваша компания</p><h2>Все друзья <span>{data.friends.length}</span></h2></div><label>⌕<input value={query} onChange={event=>setQuery(event.target.value)} placeholder="Найти друга…"/></label><Link href="/chats" className="flex h-[34px] items-center rounded-lg bg-surface-2 px-3 text-[9px]">＋ Сообщество</Link></div>
      {!visible.length ? <div className="panel p-10 text-center text-text-muted">{query ? "Никого не найдено." : "Пока здесь пусто. Откройте профиль участника в чате и отправьте заявку."}</div> :
      <div className={f.friendGrid}>{visible.map(user=><article key={user.nick}><div className={f.cardGlow}/><div className={f.personTop}><Avatar id={user.avatar} size={42} nick={user.nick}/><button disabled={!!busy} onClick={()=>act("remove",user.nick)} aria-label={`Удалить ${user.nick} из друзей`}>•••</button></div><b>{user.nick}</b><small>пользователь AniPulse</small><div className={f.common}><span>{user.favoriteGenre || "Аниме"}</span><span>{user.ratingsCount ? `${user.ratingsCount} оценок` : "Новый друг"}</span></div><div className={f.watching}><i>♥</i><span><small>КОЛЛЕКЦИЯ</small><b>{user.stats?.favoritesCount || 0} любимых тайтлов · {user.commentsCount} комментариев</b></span></div><div className={f.cardActions}><Link className="flex-1 rounded-md bg-primary p-2 text-center text-[8px] text-white" href={`/dm/${encodeURIComponent(user.nick)}`}>✉ Написать</Link><Link className="flex-1 rounded-md border border-border bg-surface-2 p-2 text-center text-[8px]" href={`/u/${encodeURIComponent(user.nick)}`}>Профиль →</Link></div></article>)}</div>}
    </section>
  </div>;
}
