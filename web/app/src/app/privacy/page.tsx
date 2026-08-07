export const metadata = { title: "Конфиденциальность — AniPulse" };
export default function Privacy() {
  return (
    <div className="mx-auto max-w-3xl px-6 py-10">
      <h1 className="pulse-text-gradient mb-4 text-2xl font-bold">Политика конфиденциальности</h1>
      <div className="space-y-3 text-text-muted">
        <p>AniPulse не собирает лишних данных. При создании аккаунта мы храним ник, почту (для входа и восстановления) и пароль в защищённом виде.</p>
        <p>Каталог и метаданные предоставляются Shikimori, видео — сторонними источниками (Kodik, AniLibria) и не хранятся на наших серверах.</p>
        <p>Для входа через сторонние сервисы используются VK ID и Яндекс OAuth.</p>
        <p>Для обезличенной статистики создаётся случайный локальный идентификатор. Учитываются первый запуск, активность приложения или сайта, версия и примерная длительность активной сессии. Постоянный сбор в фоне и рекламное профилирование не выполняются.</p>
        <p>Контакт: <a className="text-primary" href="mailto:anipulse.noreply@yandex.ru">anipulse.noreply@yandex.ru</a></p>
      </div>
    </div>
  );
}
