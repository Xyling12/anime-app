/** Соцчасть: чат, ЛС, друзья, уведомления, комментарии, карточка пользователя. */
import { api } from "./api";
import type { ChatMessage } from "./types";

export interface DmThread {
  withNick: string;
  withAvatar: number;
  withOnline: boolean;
  lastText: string;
  lastAt: number;
  unread: number;
}
export interface DmMessage {
  id: number;
  from: string;
  fromAvatar: number;
  to: string;
  text: string;
  at: number;
}
export interface Notification {
  id: number;
  type: string;
  from: string;
  text: string;
  source?: string;
  at: number;
  read: boolean;
}
export interface UserCard {
  nick: string;
  avatar: number;
  bio: string;
  createdAt?: number;
  lastSeen?: number;
  online: boolean;
  favoriteGenre?: string;
  stats?: { watchedEpisodes: number; watchMinutes: number; startedTitles: number; favoritesCount: number };
  commentsCount: number;
  ratingsCount: number;
  friendState?: string;
}

// Общий чат
export const chat = (after = 0) => api.get<ChatMessage[]>(`chat?after=${after}`);
export const sendChat = (text: string, replyTo?: number) =>
  api.post<ChatMessage>("chat", { text, replyTo }, true);

// Комментарии к тайтлу
export const comments = (animeId: string) => api.get<ChatMessage[]>(`comments?animeId=${animeId}`);
export const sendComment = (animeId: string, text: string) =>
  api.post<ChatMessage>("comments", { animeId, text }, true);

// ЛС
export const dmList = () => api.get<DmThread[]>("dm/list", true);
export const dmThread = (withNick: string, after = 0) =>
  api.get<DmMessage[]>(`dm?with=${encodeURIComponent(withNick)}&after=${after}`, true);
export const dmSend = (to: string, text: string) => api.post<DmMessage>("dm", { to, text }, true);

// Друзья
export const friends = () =>
  api.get<{ friends: UserCard[]; incoming: UserCard[] }>("friends", true);
export const friendAction = (action: "add" | "accept" | "decline" | "remove", nick: string) =>
  api.post<{ state: string; error?: string }>(`friends/${action}`, { nick }, true);

// Уведомления
export const notifications = (after = 0) => api.get<Notification[]>(`notifications?after=${after}`, true);
export const markNotificationsRead = () => api.post("notifications/read", {}, true);

// Карточка пользователя
export const userCard = (nick: string) =>
  api.get<UserCard>(`user?nick=${encodeURIComponent(nick)}`, true);
