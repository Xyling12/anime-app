"use client";

import { useEffect, useMemo, useState } from "react";
import styles from "./DesignStudio.module.css";
import c from "./Community.module.css";
import p from "./PulseCinemaApp.module.css";
import f from "./Friends.module.css";

type ScreenKey =
  | "home" | "catalog" | "air" | "library" | "profile" | "title" | "player"
  | "community" | "chat" | "dm" | "friends" | "notifications" | "auth" | "states";

const concepts = [
  { id: "cinema", number: "01", name: "Pulse Cinema", mood: "Кинематографичный · эмоциональный", note: "Рекомендуемое направление: фирменный пульс, крупные премьеры и строгая продуктовая логика." },
  { id: "tokyo", number: "02", name: "Neon Tokyo", mood: "Технологичный · социальный", note: "Ночной цифровой Токио: модульная сетка, cyan-акценты и сообщество в центре опыта." },
  { id: "manga", number: "03", name: "Manga Editorial", mood: "Редакционный · светлый", note: "Журнальная композиция, бумажная фактура и выразительная типографика вместо привычного стриминга." },
  { id: "club", number: "04", name: "Otaku Club", mood: "Тёплый · комьюнити-first", note: "Уютный клуб для совместного просмотра: друзья, активность и разговоры становятся частью открытия аниме." },
  { id: "os", number: "05", name: "Pulse OS", mood: "Минимальный · data-rich", note: "Зрелый продуктовый интерфейс: высокая плотность данных, скорость и доступность без визуального шума." },
] as const;

const groups: { label: string; items: { key: ScreenKey; label: string; icon: string }[] }[] = [
  { label: "Основное", items: [
    { key: "home", label: "Главная", icon: "⌂" }, { key: "catalog", label: "Каталог", icon: "▦" },
    { key: "air", label: "Эфир", icon: "◷" }, { key: "library", label: "Моё", icon: "♡" },
    { key: "profile", label: "Профиль", icon: "◎" },
  ]},
  { label: "Просмотр", items: [
    { key: "title", label: "Тайтл", icon: "▶" }, { key: "player", label: "Плеер", icon: "▰" },
  ]},
  { label: "Сообщество", items: [
    { key: "community", label: "Чаты", icon: "◉" }, { key: "chat", label: "Общий чат", icon: "◌" },
    { key: "dm", label: "Сообщения", icon: "✉" }, { key: "friends", label: "Друзья", icon: "♧" },
    { key: "notifications", label: "Уведомления", icon: "●" },
  ]},
  { label: "Система", items: [
    { key: "auth", label: "Вход и настройки", icon: "⚙" }, { key: "states", label: "Состояния", icon: "◇" },
  ]},
];

const posters = [
  ["Провожающая в последний путь Фрирен", "9.12", "24 серия", "a"],
  ["Монолог фармацевта", "8.94", "12 серия", "b"],
  ["Кайдзю № 8", "8.61", "7 серия", "c"],
  ["Человек-бензопила", "8.48", "12 серий", "d"],
  ["Поднятие уровня в одиночку", "8.72", "9 серия", "e"],
] as const;

function Brand() {
  return <div className={styles.brand}><span className={styles.brandMark}>A<span>⌁</span></span><b>AniPulse</b></div>;
}

function PosterCard({ item, wide = false }: { item: typeof posters[number]; wide?: boolean }) {
  return <article className={`${styles.posterCard} ${wide ? styles.wideCard : ""}`}>
    <div className={`${styles.posterArt} ${styles[`art${item[3]}`]}`}><span>{item[2]}</span><i>♥ 8.7</i></div>
    <div className={styles.posterText}><b>{item[0]}</b><small>★ {item[1]} · Shikimori</small></div>
  </article>;
}

function Shell({ screen, onScreen, children }: { screen: ScreenKey; onScreen: (s: ScreenKey) => void; children: React.ReactNode }) {
  const main = groups[0].items;
  return <div className={styles.product}>
    <aside className={styles.side}>
      <Brand />
      <nav>{main.map(x => <button key={x.key} className={screen === x.key ? styles.active : ""} onClick={() => onScreen(x.key)}><span>{x.icon}</span>{x.label}</button>)}</nav>
      <div className={styles.sideCommunity}>
        <small>СООБЩЕСТВО</small>
        <button onClick={() => onScreen("community")}><span>◉</span>Чаты <i>7</i></button>
        <button onClick={() => onScreen("friends")}><span>♧</span>Друзья</button>
      </div>
      <button className={styles.userMini} onClick={() => onScreen("profile")}><span>AK</span><b>akari</b><small>ур. 24</small></button>
    </aside>
    <div className={styles.app}>
      <header className={styles.topbar}><Brand /><div className={styles.search}>⌕ <span>Название, жанр или персонаж</span><kbd>⌘ K</kbd></div><div className={styles.topActions}><button onClick={() => onScreen("friends")} aria-label="Друзья">♧</button><button onClick={() => onScreen("dm")}>✉<i>3</i></button><button onClick={() => onScreen("notifications")}>●<i>7</i></button><span>AK</span></div></header>
      <main className={styles.canvas}>{children}</main>
      <nav className={styles.mobileNav}>{main.map(x => <button key={x.key} className={screen === x.key ? styles.active : ""} onClick={() => onScreen(x.key)}><span>{x.icon}</span>{x.label}</button>)}</nav>
    </div>
  </div>;
}

function HomeScreen() {
  return <><section className={styles.hero}>
    <div className={styles.heroCopy}><span className={styles.live}>● НОВАЯ СЕРИЯ</span><p className={styles.kicker}>ВЫБОР РЕДАКЦИИ · ФЭНТЕЗИ</p><h1>Провожающая в последний путь Фрирен</h1><p>После победы над Королём демонов эльфийка Фрирен отправляется в путешествие, чтобы понять людей и ценность коротких мгновений.</p><div className={styles.meta}>★ 9.12 <span>TV · 2023</span><span>28 серий</span><b>16+</b></div><div className={styles.actions}><button>▶ Смотреть 24 серию</button><button>＋ В моё</button></div></div>
    <div className={styles.heroVisual}><div className={styles.moon}/><div className={styles.silhouette}>旅</div><div className={styles.pulseLine}>⌁⌁⌁</div></div>
    <div className={styles.heroIndex}>01 <span>/ 05</span></div>
  </section>
  <Section title="Продолжить просмотр" action="История">{posters.slice(0,3).map(x => <PosterCard key={x[0]} item={x} wide />)}</Section>
  <Section title="Сегодня в эфире" action="Всё расписание"><div className={styles.airRail}>{["18:30","19:05","20:10","21:45"].map((t,i)=><div key={t}><b>{t}</b><span>{posters[i][0]}</span><small>Серия {i+7}</small></div>)}</div></Section>
  <Section title="Для вас" action="Смотреть все">{posters.map(x => <PosterCard key={x[0]} item={x} />)}</Section></>;
}

function Section({ title, action, children }: { title: string; action?: string; children: React.ReactNode }) {
  return <section className={styles.section}><div className={styles.sectionHead}><h2>{title}</h2>{action && <button>{action} →</button>}</div><div className={styles.rail}>{children}</div></section>;
}

function CatalogScreen() {
  return <div className={styles.page}><PageHead eyebrow="4 892 тайтла" title="Каталог аниме" text="От культовой классики до свежих онгоингов. Найдите историю под настроение." />
    <div className={styles.catalogTools}><div className={styles.bigSearch}>⌕ <span>Поиск аниме…</span></div><button>Фильтры <i>3</i></button></div>
    <div className={styles.chips}>{["Все","Онгоинги","Высший рейтинг","Фильмы","Экшен ×","2024–2026 ×"].map((x,i)=><button className={i===0?styles.selected:""} key={x}>{x}</button>)}<button>Сбросить</button></div>
    <div className={styles.catalogBody}><aside className={styles.filters}><b>Расширенный поиск</b><label>Жанры <span>3 выбрано</span></label><div>{["Экшен","Фэнтези","Романтика","Комедия","Драма","Триллер"].map(x=><button key={x}>{x}</button>)}</div><label>Год выхода</label><div className={styles.range}>2000 <i/> 2026</div><label>Рейтинг от 7.0</label><div className={styles.range}>7.0 <i/> 10</div></aside><div className={styles.catalogResults}><div className={styles.resultsHead}><span>Найдено 1 248</span><button>По популярности⌄</button></div><div className={styles.grid}>{[...posters,...posters].map((x,i)=><PosterCard key={`${x[0]}${i}`} item={x}/>)}</div></div></div>
  </div>;
}

function AirScreen() {
  return <div className={styles.page}><PageHead eyebrow="Время указано по Москве" title="Эфир" text="Новые эпизоды на одной странице — без пропущенных премьер."/><div className={styles.tabs}><button className={styles.active}>Расписание</button><button>Обновления <i>12</i></button></div>
    <div className={styles.days}>{["Пн 27","Вт 28","Сегодня 29","Чт 30","Пт 31","Сб 01","Вс 02"].map((x,i)=><button className={i===2?styles.selected:""} key={x}>{x}</button>)}</div>
    <div className={styles.timeline}>{["10:00","13:30","18:05","20:10","22:40"].map((t,i)=><div key={t}><time>{t}</time><div className={`${styles.thumb} ${styles[`art${posters[i][3]}`]}`}/><span><b>{posters[i][0]}</b><small>Серия {i+5} · ожидается сегодня</small></span><button>＋ В моё</button></div>)}</div></div>;
}

function LibraryScreen() {
  return <div className={styles.page}><PageHead eyebrow="Ваша коллекция" title="Моё" text="Продолжайте с того же места и управляйте подписками."/><div className={styles.statStrip}>{[["12","Смотрю"],["38","В планах"],["64","Просмотрено"],["7","Подписки"]].map(x=><div key={x[1]}><b>{x[0]}</b><span>{x[1]}</span></div>)}</div><div className={styles.chips}>{["Все","Смотрю","В планах","Просмотрено","Подписки"].map((x,i)=><button className={i===1?styles.selected:""} key={x}>{x}</button>)}</div>
    <div className={styles.libraryList}>{posters.map((x,i)=><div key={x[0]}><div className={`${styles.listPoster} ${styles[`art${x[3]}`]}`}/><span><small>СМОТРЮ</small><b>{x[0]}</b><em>Серия {i+6} из 12 · осталось {23-i*3} мин</em><i><u style={{width:`${38+i*11}%`}}/></i></span><button>▶ Продолжить</button><button>•••</button></div>)}</div></div>;
}

function TitleScreen() {
  return <div className={styles.titlePage}><div className={styles.titleBackdrop}><span>葬送のフリーレン</span></div><div className={styles.titleContent}><div className={`${styles.titlePoster} ${styles.arta}`}/><div className={styles.titleInfo}><p className={styles.kicker}>SOUSOU NO FRIEREN</p><h1>Провожающая в последний путь Фрирен</h1><div className={styles.meta}>★ 9.12 Shikimori <span>♥ 8.74 AniPulse</span><span>2023 · TV · 28 серий</span></div><div className={styles.chips}>{["Приключения","Драма","Фэнтези","Сёнен"].map(x=><button key={x}>{x}</button>)}</div><p>Отряд героев победил Короля демонов и принёс миру покой. Спустя десятилетия Фрирен начинает понимать, как мало знала о своих спутниках.</p><div className={styles.actions}><button>▶ Продолжить · 24 серия</button><button>♡ В моём</button><button>● Подписка</button></div></div></div>
    <div className={styles.detailGrid}><section><h2>Серии и озвучка</h2><div className={styles.tabs}><button className={styles.active}>AniLibria · 1080p</button><button>2×2</button><button>AniDUB</button><button>SHIZA Project</button></div><div className={styles.episodes}>{Array.from({length:12},(_,i)=><button className={i===7?styles.selected:""} key={i}><b>{i+1}</b><span>{i<7?"✓ Просмотрено":i===7?"16:42 / 24:10":"24 мин"}</span></button>)}</div></section><aside><h2>Рейтинги</h2><div className={styles.ratings}><div><b>★ 9.12</b><span>Shikimori · 188K</span></div><div><b>♥ 8.74</b><span>AniPulse · 4.2K</span></div></div><h3>Ваша оценка</h3><div className={styles.score}>{[1,2,3,4,5,6,7,8,9,10].map(x=><button className={x===9?styles.selected:""} key={x}>{x}</button>)}</div></aside></div>
    <Section title="Обсуждение · 326" action="По новизне"><Comments/></Section></div>;
}

function PlayerScreen() {
  return <div className={styles.player}><div className={styles.video}><div className={styles.playerTop}><button>←</button><span><b>Фрирен</b><small>24 серия · AniLibria</small></span><button>Озвучка⌄</button><button>⚙</button></div><div className={styles.playCenter}><button>−10</button><button>▶</button><button>+10</button></div><button className={styles.skip}>Пропустить опенинг →</button><div className={styles.controls}><div className={styles.seek}><i/><u/></div><span>16:42 / 24:10</span><button>Следующая серия</button><button>1080p</button><button>▣</button></div></div><aside className={styles.discussion}><div><h2>Обсуждение серии</h2><button>×</button></div><Comments/><label><input placeholder="Написать комментарий…"/><button>↑</button></label></aside></div>;
}

function CommunityScreen({ onScreen }: { onScreen: (s: ScreenKey)=>void }) {
  const cards: [ScreenKey,string,string,string,string][] = [
    ["dm","Сообщения","3 новых","Личные диалоги","✉"],
    ["friends","Друзья","Заявки","Ваш круг общения","♧"],
    ["notifications","Уведомления","7 новых","Ответы, теги и события","●"],
  ];
  return <div className={`${styles.page} ${c.page}`}>
    <div className={c.hero}><div><p className={styles.kicker}>Место для своих</p><h1>Сообщество</h1><p>Обсуждайте любимые истории, делитесь находками и находите людей с похожим вкусом.</p></div><div className={c.pulse}>⌁⌁⌁</div></div>
    <div className={c.layout}>
      <button className={c.featured} onClick={()=>onScreen("chat")}><span className={c.featuredIcon}>◉</span><span className={c.featuredCopy}><small>ОБЩЕЕ ПРОСТРАНСТВО</small><b>Общий чат</b><em>Разговоры о новых сериях, находках и всём, что хочется обсудить.</em></span><span className={c.avatars}><i>МИ</i><i>NO</i><i>YU</i><i>+12</i></span><strong>Открыть чат <i>→</i></strong></button>
      <div className={c.grid}>{cards.map((x)=><button key={x[0]} onClick={()=>onScreen(x[0])}><i>{x[4]}</i><span><b>{x[1]}</b><small>{x[3]}</small></span><em>{x[2]}</em><strong>→</strong></button>)}</div>
    </div>
    <section className={c.discuss}><div className={styles.sectionHead}><div><p className={styles.kicker}>Свежие разговоры</p><h2>Сейчас обсуждают</h2></div><button>Все обсуждения →</button></div><div className={c.discussGrid}>
      <button><span className={c.discussArt}/><span><small>ОБСУЖДЕНИЕ СЕРИИ</small><b>24 серия «Фрирен»</b><em>О путешествии, памяти и сцене после титров</em><i>Без спойлеров · 18 новых ответов</i></span><strong>→</strong></button>
      <button><span className={c.discussNumber}>01</span><span><small>РЕКОМЕНДАЦИИ</small><b>Что смотреть этим летом?</b><em>Делимся свежими находками сезона</em><i>9 новых ответов</i></span><strong>→</strong></button>
      <button><span className={c.discussNumber}>02</span><span><small>ПОДБОРКИ</small><b>Лучшие короткие сериалы</b><em>Истории, которые можно посмотреть за вечер</em><i>5 новых ответов</i></span><strong>→</strong></button>
    </div></section>
  </div>;
}

const people = [["mika","МИ","Ты видел новую серию?","18:42"],["nori","NO","Это было неожиданно…","17:08"],["yuki","YU","Отправил вам заявку в друзья","Вчера"],["senpai","SE","Спасибо за рекомендацию!","Пн"]];

function ChatScreen({ dm=false }: { dm?: boolean }) {
  const channels = [["#","Общий","Разговоры обо всём"],["▶","Новые серии","Обсуждения премьер"],["♡","Рекомендации","Что посмотреть дальше"],["◇","Помощь","Вопросы по AniPulse"]];
  return <div className={styles.chatLayout}>
    <aside className={styles.chatList}>
      <h2>{dm?"Сообщения":"Пространства"}</h2>
      <div className={styles.bigSearch}>⌕ <span>{dm?"Поиск диалогов":"Поиск обсуждений"}</span></div>
      {dm ? people.map((p,i)=><button className={i===0?styles.active:""} key={p[0]}><i>{p[1]}</i><span><b>{p[0]}</b><small>{p[2]}</small></span><time>{p[3]}</time>{i<2&&<em>{i+1}</em>}</button>) : channels.map((p,i)=><button className={i===0?styles.active:""} key={p[1]}><i>{p[0]}</i><span><b>{p[1]}</b><small>{p[2]}</small></span></button>)}
    </aside>
    <section className={styles.thread}>
      <header><i>{dm?"МИ":"#"}</i><span><b>{dm?"mika":"Общий чат"}</b><small>{dm?"Личный диалог":"Открытое обсуждение сообщества"}</small></span><button>⌕</button><button>•••</button></header>
      <div className={styles.messages}><p className={styles.date}>Сегодня</p><Message user="mika" time="18:38">Кто уже посмотрел новую серию? Только без спойлеров 👀</Message><Message user="akari" time="18:40" own>Я! Финальная сцена просто невероятная</Message><Message user="mika" time="18:42">Согласен. <span className={styles.spoiler}>Показать спойлер</span></Message><Message user="nori" time="18:43">Кстати, после титров есть важный момент</Message></div>
      <footer><button>＋</button><input placeholder={dm?"Написать mika…":"Сообщение в общий чат… Используйте @ для упоминания"}/><button>↑</button></footer>
    </section>
    <aside className={styles.members}>
      <h3>{dm?"О пользователе":"О пространстве"}</h3>
      {dm ? <><div><i>МИ</i><span><b>mika</b><small>12 общих тайтлов</small></span></div><button className={p.profileAction}>Открыть профиль</button></> : <div className={p.channelAbout}><i>#</i><span><b>Общий чат AniPulse</b><small>Дружелюбное общение без спойлеров. Уважайте собеседников и скрывайте детали сюжета.</small></span></div>}
    </aside>
  </div>;
}

function Message({user,time,own,children}:{user:string;time:string;own?:boolean;children:React.ReactNode}) {
  return <div className={`${styles.message} ${own?styles.own:""}`}><i>{user.slice(0,2).toUpperCase()}</i><span><b>{user} <time>{time}</time></b><p>{children}</p></span></div>;
}

function FriendsScreen() {
  const friends = [...people,...people].map((person,index)=>({name:person[0], initials:person[1], index}));
  return <div className={f.page}>
    <header className={f.hero}>
      <div><p className={styles.kicker}>Ваш круг AniPulse</p><h1>Друзья</h1><p>Находите людей с похожим вкусом, делитесь тайтлами и возвращайтесь к обсуждениям вместе.</p></div>
      <div className={f.heroPeople}><i>МИ</i><i>NO</i><i>YU</i><i>SE</i><span>＋</span></div>
    </header>
    <section className={f.requests}>
      <div className={f.sectionTitle}><span><small>НОВЫЕ ЗНАКОМСТВА</small><h2>Входящие заявки</h2></span><em>2 ожидают ответа</em></div>
      <div className={f.requestGrid}>{people.slice(0,2).map((person,index)=><article key={person[0]}>
        <i>{person[1]}</i><span><b>{person[0]}</b><small>@{person[0]} · {12+index*4} общих тайтлов</small><em>{index===0?"Любит фэнтези и приключения":"Смотрит новинки сезона"}</em></span>
        <div><button>Принять</button><button aria-label={`Отклонить заявку ${person[0]}`}>×</button></div>
      </article>)}</div>
    </section>
    <section className={f.friends}>
      <div className={f.friendsHead}><div><p className={styles.kicker}>Ваша компания</p><h2>Все друзья <span>42</span></h2></div><label>⌕<input placeholder="Найти друга…"/></label><button>＋ Найти людей</button></div>
      <div className={f.friendGrid}>{friends.map((person)=><article key={`${person.name}${person.index}`}>
        <div className={f.cardGlow}/><div className={f.personTop}><i>{person.initials}</i><button>•••</button></div>
        <b>{person.name}{person.index}</b><small>@{person.name} · друзья с мая</small>
        <div className={f.common}><span>Фэнтези</span><span>{person.index%2?"Драма":"Приключения"}</span></div>
        <div className={f.watching}><i>▶</i><span><small>НЕДАВНО СМОТРЕЛ</small><b>{posters[person.index%posters.length][0]}</b></span></div>
        <div className={f.cardActions}><button>✉ Написать</button><button>Профиль →</button></div>
      </article>)}</div>
    </section>
  </div>;
}

function NotificationsScreen() {
  return <div className={styles.page}><PageHead eyebrow="7 непрочитанных" title="Уведомления" text="Ответы, упоминания, заявки и новые серии подписок."/><div className={styles.notificationList}>{[
    ["＠","mika упомянул вас","в обсуждении 24 серии «Фрирен»","2 мин"],
    ["✉","Новое сообщение от nori","«Это было неожиданно…»","18 мин"],
    ["♧","yuki хочет добавить вас в друзья","12 общих тайтлов","1 ч"],
    ["▶","Вышла новая серия","«Кайдзю № 8» · серия 7","3 ч"],
    ["♥","Ваш комментарий оценили","5 новых реакций","вчера"],
  ].map((x,i)=><button className={i<3?styles.unread:""} key={x[1]}><i>{x[0]}</i><span><b>{x[1]}</b><small>{x[2]}</small></span><time>{x[3]}</time></button>)}</div></div>;
}

function ProfileScreen() {
  return <div className={styles.page}><div className={styles.profileHero}><div className={styles.avatar}>AK<i>●</i></div><div><p className={styles.kicker}>УЧАСТНИК С МАЯ 2026</p><h1>akari</h1><p>@akari · люблю фэнтези, тёплый чай и хорошие эндинги</p><button>Редактировать профиль</button></div><div className={styles.level}><b>24</b><span>уровень</span><i><u/></i><small>1 840 / 2 400 XP</small></div></div><div className={styles.profileGrid}><section><h2>Статистика</h2><div className={styles.statStrip}>{[["12","Смотрю"],["38","В планах"],["64","Просмотрено"],["428","Часов"]].map(x=><div key={x[1]}><b>{x[0]}</b><span>{x[1]}</span></div>)}</div><h2>Последняя активность</h2><div className={styles.activity}>{posters.slice(0,3).map((x,i)=><div key={x[0]}><i>▶</i><span><b>{x[0]}</b><small>Посмотрена серия {i+8} · {i+1} ч назад</small></span></div>)}</div></section><aside><h2>Настройки плеера</h2>{["Автопропуск опенинга","Пропуск повторов","Следующая серия автоматически","Уведомления о сериях"].map((x,i)=><label key={x}><span><b>{x}</b><small>{i<3?"Можно изменить в плеере":"Только для подписок"}</small></span><input type="checkbox" defaultChecked={i!==1}/></label>)}<button className={styles.danger}>Выйти из аккаунта</button></aside></div></div>;
}

function AuthScreen() {
  return <div className={styles.authPage}><div className={styles.authVisual}><Brand/><p className={styles.kicker}>ОДИН АККАУНТ · ВСЕ УСТРОЙСТВА</p><h1>Ваши истории продолжаются здесь</h1><p>Синхронизируйте коллекцию, прогресс и друзей между сайтом и Android-приложением.</p><div className={styles.authPulse}>⌁⌁⌁</div></div><form className={styles.authCard}><span className={styles.kicker}>С возвращением</span><h2>Войти в AniPulse</h2><p>Нет аккаунта? <a>Создать бесплатно</a></p><label>Email или ник<input placeholder="akari@example.com"/></label><label>Пароль<a>Забыли пароль?</a><input type="password" defaultValue="password"/></label><button>Войти</button><div className={styles.or}><i/>или<i/></div><button className={styles.oauth}>Я Войти с Яндекс ID</button><button className={styles.oauth}>VK Войти через VK ID</button><small>Продолжая, вы принимаете условия и политику обработки данных.</small></form></div>;
}

function StatesScreen() {
  return <div className={styles.page}><PageHead eyebrow="Design system" title="Состояния интерфейса" text="Каждый концепт предусматривает реальные загрузки, ошибки, пустые списки и ограничения доступа."/><div className={styles.statesGrid}>
    <div><span className={styles.spinner}/><h3>Загрузка каталога</h3><p>Сохраняем структуру экрана без скачков.</p></div>
    <div><i>◇</i><h3>Здесь пока пусто</h3><p>Добавьте тайтл в «Моё», и он появится здесь.</p><button>Открыть каталог</button></div>
    <div><i>↻</i><h3>Не удалось загрузить</h3><p>Проверьте соединение и попробуйте ещё раз.</p><button>Повторить</button></div>
    <div><i>🔒</i><h3>Нужен аккаунт</h3><p>Войдите, чтобы писать сообщения и добавлять друзей.</p><button>Войти</button></div>
    <div><i>☁</i><h3>Вы не в сети</h3><p>Коллекция доступна, а свежие данные обновятся позже.</p></div>
    <div><i>18+</i><h3>Контент скрыт</h3><p>Измените возрастные настройки для просмотра.</p><button>Настройки</button></div>
  </div></div>;
}

function Comments() {
  return <div className={styles.comments}><Message user="mika" time="18 мин">Очень бережная экранизация. Музыка в конце серии — отдельное искусство.</Message><Message user="nori" time="7 мин">Сцена с письмом разбила мне сердце. <span className={styles.spoiler}>Показать спойлер</span></Message><label><input placeholder="Присоединиться к обсуждению…"/><button>Отправить</button></label></div>;
}

function PageHead({eyebrow,title,text}:{eyebrow:string;title:string;text:string}) {
  return <header className={styles.pageHead}><p className={styles.kicker}>{eyebrow}</p><h1>{title}</h1><p>{text}</p></header>;
}

function Screen({screen,onScreen}:{screen:ScreenKey;onScreen:(s:ScreenKey)=>void}) {
  switch(screen) {
    case "home": return <HomeScreen/>; case "catalog": return <CatalogScreen/>; case "air": return <AirScreen/>;
    case "library": return <LibraryScreen/>; case "profile": return <ProfileScreen/>; case "title": return <TitleScreen/>;
    case "player": return <PlayerScreen/>; case "community": return <CommunityScreen onScreen={onScreen}/>;
    case "chat": return <ChatScreen/>; case "dm": return <ChatScreen dm/>; case "friends": return <FriendsScreen/>;
    case "notifications": return <NotificationsScreen/>; case "auth": return <AuthScreen/>; default: return <StatesScreen/>;
  }
}

export function DesignStudio() {
  const [concept, setConcept] = useState(0);
  const [screen, setScreen] = useState<ScreenKey>("home");
  useEffect(() => {
    document.body.classList.add("design-studio-active");
    return () => document.body.classList.remove("design-studio-active");
  }, []);
  const current = concepts[concept];
  const screenName = useMemo(()=>groups.flatMap(g=>g.items).find(x=>x.key===screen)?.label,[screen]);
  return <div className={styles.studio} data-design-studio>
    <header className={styles.studioHeader}><div><span>ANIPULSE · DESIGN STUDY 2026</span><h1>Пять характеров.<br/>Один ритм.</h1></div><p>Полный интерактивный дизайн-проект сайта на основе всех функций Android-приложения. Выберите направление и экран для сравнения.</p></header>
    <div className={styles.conceptTabs}>{concepts.map((x,i)=><button key={x.id} className={i===concept?styles.current:""} onClick={()=>setConcept(i)}><span>{x.number}</span><b>{x.name}</b><small>{x.mood}</small></button>)}</div>
    <section className={styles.conceptIntro}><div><span>КОНЦЕПЦИЯ {current.number}</span><h2>{current.name}</h2><p>{current.note}</p></div><div className={styles.palette}><i/><i/><i/><i/><i/></div></section>
    <div className={styles.workspace}>
      <aside className={styles.screenPicker}><div><span>КАРТА ЭКРАНОВ</span><b>{screenName}</b></div>{groups.map(g=><section key={g.label}><small>{g.label}</small>{g.items.map(x=><button key={x.key} className={screen===x.key?styles.current:""} onClick={()=>setScreen(x.key)}><span>{x.icon}</span>{x.label}</button>)}</section>)}</aside>
      <div className={`${styles.preview} ${styles[current.id]}`} data-concept={current.id}><div className={styles.browserBar}><i/><i/><i/><span>anipulse.ru/{screen==="home"?"":screen}</span><em>Интерактивный макет · 1440</em></div><Shell screen={screen} onScreen={setScreen}><Screen screen={screen} onScreen={setScreen}/></Shell></div>
    </div>
    <footer className={styles.studioFooter}><Brand/><p>В проект включены основные вкладки, тайтл, плеер, чаты, сообщения, друзья, уведомления, авторизация, настройки и системные состояния.</p><span>14 экранов × 5 концепций = 70 представлений</span></footer>
  </div>;
}

export function PulseCinemaApp() {
  const [screen, setScreen] = useState<ScreenKey>("home");
  useEffect(() => {
    document.body.classList.add("pulse-app-active");
    return () => document.body.classList.remove("pulse-app-active");
  }, []);
  return <div className={`${styles.preview} ${styles.cinema} ${p.production}`} data-pulse-app>
    <Shell screen={screen} onScreen={setScreen}><Screen screen={screen} onScreen={setScreen}/></Shell>
  </div>;
}
