"""Shadow RP Windows companion preview for inspecting local Minecraft Forge files.

This tool does not install a Minecraft mod or modify user files.
The actual Forge 1.20.1 mod is a separate, unfinished deliverable.
"""
from __future__ import annotations

import os
import re
import zipfile
from pathlib import Path
import tkinter as tk
from tkinter import filedialog, messagebox

BG = "#090e1c"
PANEL = "#172443"
BLUE = "#58c9ff"
TEXT = "#e7f0ff"


def default_minecraft_dir() -> Path:
    appdata = os.environ.get("APPDATA")
    return Path(appdata, ".minecraft") if appdata else Path.home() / ".minecraft"


def duplicate_mod_names(mods: list[str]) -> list[str]:
    """Flag same-named JARs across mod folders without altering either copy."""
    counts: dict[str, int] = {}
    for mod in mods:
        key = mod.rsplit("/", 1)[-1].casefold()
        counts[key] = counts.get(key, 0) + 1
    return sorted(name for name, count in counts.items() if count > 1)


def inspect_instance(folder: Path) -> dict:
    """Read-only inspection of a Minecraft directory. Never writes to disk."""
    if not folder.is_dir():
        return {"exists": False, "forge": False, "versions": [], "mods": [],
                "empty_mods": [], "invalid_mods": [], "fabric_mods": [], "neoforge_mods": [], "duplicates": [],
                "message": "Папка Minecraft не найдена."}
    try:
        versions_dir = folder / "versions"
        versions = sorted(p.name for p in versions_dir.iterdir() if p.is_dir()) if versions_dir.is_dir() else []
        mods_dir = folder / "mods"
        mods = []
        if mods_dir.is_dir():
            mods.extend(p.name for p in mods_dir.iterdir()
                        if p.is_file() and p.suffix.lower() == ".jar")
            # Some mod managers keep Forge 1.20.1 JARs in a version folder.
            # Inspect it without changing the user's mod installation.
            versioned = mods_dir / "1.20.1"
            if versioned.is_dir():
                mods.extend("1.20.1/" + p.name for p in versioned.iterdir()
                            if p.is_file() and p.suffix.lower() == ".jar")
            mods.sort(key=str.lower)
        # Empty JARs are not valid mods and may indicate interrupted downloads.
        # Only inspect file sizes; never delete or modify user files.
        empty_mods = [name for name in mods
                      if (mods_dir / name).stat().st_size == 0]
        # JARs must have a valid ZIP central directory. A PK header alone
        # can also belong to a truncated download; inspect without extracting.
        invalid_mods = []
        fabric_mods = []
        neoforge_mods = []
        for name in mods:
            if name in empty_mods:
                continue
            jar_path = mods_dir / name
            if not zipfile.is_zipfile(jar_path):
                invalid_mods.append(name)
                continue
            # Reading ZIP metadata does not execute or extract mod contents.
            try:
                with zipfile.ZipFile(jar_path) as jar:
                    members = set(jar.namelist())
                    if "fabric.mod.json" in members and "META-INF/mods.toml" not in members:
                        fabric_mods.append(name)
                    # NeoForge uses a different mod descriptor. Warn only when
                    # the Forge descriptor is absent; do not edit any JAR.
                    if "META-INF/neoforge.mods.toml" in members and "META-INF/mods.toml" not in members:
                        neoforge_mods.append(name)
            except zipfile.BadZipFile:
                invalid_mods.append(name)
    except OSError as exc:
        return {"exists": True, "forge": False, "versions": [], "mods": [],
                "empty_mods": [], "invalid_mods": [], "fabric_mods": [], "neoforge_mods": [], "duplicates": [],
                "message": f"Не удалось прочитать файлы: {exc}"}
    forge = any("forge" in v.lower() and re.search(r"(?<![\d.])1\.20\.1(?![\d.])", v) for v in versions)
    message = ("Forge 1.20.1 обнаружен" if forge else
               "Forge 1.20.1 не найден среди установленных версий")
    return {"exists": True, "forge": forge, "versions": versions,
            "mods": mods, "duplicates": duplicate_mod_names(mods),
            "empty_mods": empty_mods, "invalid_mods": invalid_mods,
            "fabric_mods": fabric_mods, "neoforge_mods": neoforge_mods,
            "message": message}


class ShadowCompanion(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title("Shadow RP — Windows Companion Preview")
        self.geometry("850x650")
        self.minsize(650, 500)
        self.configure(bg=BG)
        self.folder = tk.StringVar(value=str(default_minecraft_dir()))
        self._build()
        self.refresh()

    def _button(self, parent, label, action):
        return tk.Button(parent, text=label, command=action, bg=PANEL, fg=TEXT,
                         activebackground="#2857a0", activeforeground=TEXT,
                         relief="flat", padx=13, pady=9, cursor="hand2")

    def _build(self):
        tk.Label(self, text="VEYRION  /  SHADOW RP", font=("Segoe UI", 23, "bold"),
                 bg=BG, fg=BLUE).pack(anchor="w", padx=24, pady=(22, 4))
        tk.Label(self, text="Windows companion • только проверка локальных файлов",
                 bg=BG, fg=TEXT, font=("Segoe UI", 11)).pack(anchor="w", padx=24)
        folder_row = tk.Frame(self, bg=BG)
        folder_row.pack(fill="x", padx=24, pady=20)
        tk.Entry(folder_row, textvariable=self.folder, bg=PANEL, fg=TEXT,
                 insertbackground=TEXT, relief="flat", font=("Segoe UI", 12)).pack(
                     side="left", fill="x", expand=True, ipady=10)
        self._button(folder_row, "Обзор", self.browse).pack(side="left", padx=6)
        self._button(folder_row, "Проверить", self.refresh).pack(side="left")
        self.status = tk.Label(self, text="", bg=BG, fg=BLUE,
                               font=("Segoe UI", 15, "bold"))
        self.status.pack(anchor="w", padx=24, pady=(0, 8))
        self.details = tk.Label(self, text="", bg=BG, fg=TEXT,
                                font=("Segoe UI", 11), justify="left")
        self.details.pack(anchor="w", padx=24)
        tk.Label(self, text="Обнаруженные моды (.jar)", bg=BG, fg=BLUE,
                 font=("Segoe UI", 13, "bold")).pack(anchor="w", padx=24, pady=(20, 8))
        self.mods = tk.Listbox(self, bg=PANEL, fg=TEXT, relief="flat",
                               selectbackground="#2857a0", font=("Segoe UI", 11))
        self.mods.pack(fill="both", expand=True, padx=24)
        controls = tk.Frame(self, bg=BG)
        controls.pack(fill="x", padx=24, pady=14)
        self._button(controls, "Открыть папку mods", self.open_mods).pack(side="left", padx=4)
        self._button(controls, "Открыть Minecraft Launcher", self.open_launcher).pack(side="left", padx=4)
        tk.Label(self, text="Мод Shadow RP пока не собран. Установка и синхронизация не реализованы.",
                 bg=BG, fg="#a9bddd", font=("Segoe UI", 10)).pack(
                     anchor="w", padx=24, pady=(0, 16))

    def browse(self):
        folder = filedialog.askdirectory(title="Выбери папку .minecraft")
        if folder:
            self.folder.set(folder)
            self.refresh()

    def refresh(self):
        report = inspect_instance(Path(self.folder.get()).expanduser())
        self.status.config(text=report["message"])
        self.details.config(text=(
            f"Версий Minecraft: {len(report['versions'])}  •  "
            f"JAR-модов: {len(report['mods'])}\n"
            "Проверяется только наличие папки Forge 1.20.1, не совместимость модов."
            + (f"\nВнимание: повторяющиеся JAR: {', '.join(report['duplicates'])}"
               if report.get("duplicates") else "")
            + ("\nВнимание: пустые JAR-файлы: " + ", ".join(report["empty_mods"])
               if report.get("empty_mods") else "")
            + ("\nВнимание: повреждённые или не-ZIP JAR: " + ", ".join(report["invalid_mods"])
               if report.get("invalid_mods") else "")
            + ("\nВозможно несовместимые Fabric-моды для Forge: " + ", ".join(report["fabric_mods"])
               if report.get("fabric_mods") else "")
            + ("\nВозможно несовместимые NeoForge-моды для Forge: " + ", ".join(report["neoforge_mods"])
               if report.get("neoforge_mods") else "")
        ))
        self.mods.delete(0, tk.END)
        for name in report["mods"]:
            self.mods.insert(tk.END, name)

    def open_mods(self):
        folder = Path(self.folder.get()).expanduser() / "mods"
        if not folder.is_dir():
            messagebox.showinfo("Shadow RP", "Папка mods не найдена. Файлы не изменены.")
            return
        try:
            os.startfile(str(folder))
        except OSError as exc:
            messagebox.showerror("Shadow RP", str(exc))

    def open_launcher(self):
        try:
            os.startfile("minecraft:")
        except OSError:
            messagebox.showinfo("Shadow RP",
                                "Не удалось открыть Minecraft Launcher. Запусти его вручную.")


if __name__ == "__main__":
    ShadowCompanion().mainloop()
