export const metadata = { title: "Для правообладателей — AniPulse" };
export default function RightHolders() {
  return (
    <div className="mx-auto max-w-3xl px-6 py-10">
      <h1 className="pulse-text-gradient mb-4 text-2xl font-bold">Для правообладателей</h1>
      <div className="space-y-3 text-text-muted">
        <p>AniPulse не хранит и не размещает видеофайлы на своих серверах. Плеер воспроизводит контент со сторонних источников по публично доступным ссылкам.</p>
        <p>Мы можем убрать привязку конкретного тайтла в нашем каталоге, но не можем удалить сам файл у стороннего источника.</p>
        <p>По вопросам удаления привязки пишите: <a className="text-primary" href="mailto:anipulse.noreply@yandex.ru">anipulse.noreply@yandex.ru</a> — с указанием тайтла и подтверждением прав.</p>
      </div>
    </div>
  );
}
