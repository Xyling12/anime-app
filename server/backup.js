"use strict";

const fs = require("node:fs");
const path = require("node:path");
const zlib = require("node:zlib");

const DATA_DIR = process.env.ANIPULSE_DATA_DIR || path.join(__dirname, "..", "data");
const BACKUP_DIR = path.join(__dirname, "..", "backups");
const MAX_BACKUPS = 14;

function ensureDir(dir) {
  if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
}

function formatDate(date = new Date()) {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  const h = String(date.getHours()).padStart(2, "0");
  const min = String(date.getMinutes()).padStart(2, "0");
  return `${y}-${m}-${d}_${h}-${min}`;
}

async function createBackup() {
  ensureDir(BACKUP_DIR);
  if (!fs.existsSync(DATA_DIR)) {
    console.log(`[Backup] Data dir ${DATA_DIR} does not exist, nothing to back up.`);
    return;
  }

  const timestamp = formatDate();
  const backupFile = path.join(BACKUP_DIR, `anipulse-backup-${timestamp}.json.gz`);

  console.log(`[Backup] Starting backup from ${DATA_DIR} to ${backupFile}...`);

  const files = fs.readdirSync(DATA_DIR);
  const bundle = {};

  for (const file of files) {
    if (file.endsWith(".json") || file === "auth-secret") {
      const fullPath = path.join(DATA_DIR, file);
      try {
        const stat = fs.statSync(fullPath);
        if (stat.isFile()) {
          bundle[file] = fs.readFileSync(fullPath, "utf8");
        }
      } catch (err) {
        console.warn(`[Backup] Could not read ${file}:`, err.message);
      }
    }
  }

  const jsonStr = JSON.stringify(bundle);
  const compressed = zlib.gzipSync(Buffer.from(jsonStr, "utf8"));
  fs.writeFileSync(backupFile, compressed);

  console.log(`[Backup] Successfully saved ${backupFile} (${(compressed.length / 1024).toFixed(1)} KB)`);

  // Ротация: удаляем старые бэкапы сверх MAX_BACKUPS
  const allBackups = fs.readdirSync(BACKUP_DIR)
    .filter((f) => f.startsWith("anipulse-backup-") && f.endsWith(".json.gz"))
    .map((f) => ({ name: f, path: path.join(BACKUP_DIR, f), time: fs.statSync(path.join(BACKUP_DIR, f)).mtimeMs }))
    .sort((a, b) => b.time - a.time);

  if (allBackups.length > MAX_BACKUPS) {
    const toDelete = allBackups.slice(MAX_BACKUPS);
    for (const old of toDelete) {
      try {
        fs.unlinkSync(old.path);
        console.log(`[Backup] Removed old backup: ${old.name}`);
      } catch {}
    }
  }
}

if (require.main === module) {
  createBackup().catch((err) => {
    console.error("[Backup] Fatal error:", err);
    process.exit(1);
  });
}

module.exports = { createBackup };
