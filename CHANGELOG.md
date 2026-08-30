# 🚀 Полный список изменений AniPulse (v0.7.0 – v0.7.1)

---

## 📢 Вариант для Telegram-канала и Сообщества

```text
🎉 ГЛОБАЛЬНОЕ ОБНОВЛЕНИЕ ANIPULSE 0.7.1!

Мы провели масштабную работу над приложением: добавили долгожданную светлую тему, полноценный офлайн-режим для просмотра без интернета, полностью переработали ключевые экраны и меню действий.

Что нового:

🌓 1. ПОЛНОЦЕННАЯ СВЕТЛАЯ ТЕМА
• Добавлена светлая тема оформления для комфортного просмотра при дневном свете.
• Наглядный переключатель [ ☀️ Светлая ] / [ 🌙 Тёмная ] прямо в Профиле.
• Полная адаптация всех экранов: Главная, Каталог, Поиск, Расписание, Библиотека, Страница тайтла и Комментарии.

📥 2. СКАЧИВАНИЕ СЕРИЙ И ОФЛАЙН-ПЛЕЕР
• Скачивайте любые серии на устройство и смотрите их без доступа к интернету (в дороге, самолёте или за городом).
• Новая вкладка «Загрузки» в разделе «Моё» — удобное управление памятью и скачанными сериями с указанием веса файлов в МБ.
• Офлайн-плеер: мгновенный запуск скачанных серий без буферизации.

📱 3. НОВОЕ МЕНЮ ДЕЙСТВИЙ ПО СЕРИИ (BOTTOM SHEET)
• Полный редизайн окна выбора серии: современная шторка снизу с крупным номером серии, выбранной озвучкой и статусом «Офлайн».
• Кнопка «Продолжить просмотр» с точным сохранением таймкода остановки + возможность «Смотреть сначала».
• Управление загрузкой в 1 клик прямо из меню серии.

🎨 4. ОБНОВЛЕНИЕ КАТАЛОГА И ДИЗАЙНА
• Полностью обновлены карточки постеров в каталоге и поиске (чистые подложки с контуром, двойной рейтинг Shikimori + AniPulse).
• Адаптирован блок статистики онлайна и все диалоговые окна.

💬 5. ЧИСТЫЙ ИНТЕРФЕЙС И СООБЩЕСТВО
• Убраны лишние вкладки и неиспользуемые чаты — приложение стало работать ещё быстрее и легче.
• Сохранены комментарии под сериями и добавлена карточка перехода в наш официальный Telegram-канал.

⚡ 6. ОПТИМИЗАЦИЯ И СКОРОСТЬ
• Размер APK оптимизирован до ~3.5 МБ.
• Улучшена стабильность видеопотоков и плавность анимаций.
• Автоматические обновления по воздуху (OTA).

📲 Скачать последнюю версию: https://anipulsetv.ru/alapi/apk
```

---

## 🏬 Вариант для RuStore (поле «Что нового»)

```text
• 🌓 Полноценная Светлая тема с наглядным переключателем в Профиле
• 📥 Офлайн-режим: скачивание серий и просмотр без интернета
• 📂 Вкладка «Загрузки» в разделе «Моё» для управления сохранёнными сериями
• 📱 Новое удобное модальное меню действий по серии (Bottom Sheet) с таймкодами
• 🎨 Обновлён дизайн карточек каталога, поиска и раздела статистики
• 🧹 Очистка интерфейса от лишних элементов для максимальной скорости работы
• ⚡ Оптимизация плеера, быстрая буферизация видео и уменьшение размера приложения
```

---

## 💻 Технический Changelog (для GitHub Releases / Разработчиков)

```markdown
### Features & UI Enhancements
- **Light/Dark Theme System**: Implemented full dynamic Material 3 theme switching with `RootMenuViewModel` and persistent storage. Added custom `ThemeSelectorCard` segmented switch in `ProfileScreen`.
- **Offline Download Manager**: Built multi-threaded background downloader, reactive `downloadedList` StateFlow, offline playback in `PlayerViewModel`, and a dedicated "Downloads" tab in `LibraryScreen`.
- **Episode Action BottomSheet**: Replaced legacy `AlertDialog`s in `TitleScreen` with a sleek `ModalBottomSheet` displaying title, dubbing, watch progress timestamp, resume/start-over controls, and download/delete management.
- **Catalog & Search Redesign**: Re-engineered `PosterCard` with theme-adaptive surface styling, border outlines, and dual Shikimori/AniPulse rating badges.
- **Community Cleanup**: Streamlined codebase by removing obsolete direct message/chat modules, while preserving comment threads and integrating direct Telegram community cards.

### Performance & Build
- **ProGuard / R8 Optimization**: Compressed signed release binary to ~3.58 MB with fast startup and low memory footprint.
- **OTA Updates**: Synchronized `app-version.json` endpoint (`versionCode: 28`, `versionName: "0.7.1"`).
```
