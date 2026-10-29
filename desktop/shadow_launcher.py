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



def disabled_mod_names(mods_dir: Path) -> list[str]:
    """Show disabled JARs without treating them as active Forge mods."""
    disabled: list[str] = []
    for folder, prefix in ((mods_dir, ""), (mods_dir / "1.20.1", "1.20.1/")):
        if not folder.is_dir() or folder.is_symlink():
            continue
        for path in folder.iterdir():
            if (path.is_file() and not path.is_symlink() and
                    path.name.casefold().endswith((".jar.disabled", ".jar.off", ".jar.bak"))):
                disabled.append(prefix + path.name)
    return sorted(disabled, key=str.casefold)


def forge_1201_versions(versions: list[str]) -> list[str]:
    """Find Forge 1.20.1 without confusing 1.20.10 or 1.20.1.1."""
    return [version for version in versions
            if "forge" in version.casefold()
            and re.search(r"(?<![\d.])1\.20\.1(?![\d.])", version)]


def unsafe_jar_members(names: list[str]) -> bool:
    """Warn about ambiguous or unsafe ZIP paths without extracting a mod.

    Windows reserved devices, alternate data streams and trailing dots/spaces
    can resolve differently from the names stored in the JAR central directory.
    """
    reserved = re.compile(r"^(?:con|prn|aux|nul|com[1-9]|lpt[1-9])(?:\.|$)", re.I)
    invalid = re.compile(r'[\x00-\x1f<>:"|?*]')
    for name in names:
        normalized = name.replace("\\", "/")
        parts = normalized.split("/")
        if (not normalized or normalized.startswith("/") or
                re.match(r"^[A-Za-z]:", normalized)):
            return True
        for index, part in enumerate(parts):
            if not part:
                # A single final slash denotes a normal directory entry.
                if index != len(parts) - 1:
                    return True
                continue
            if (part in (".", "..") or part.rstrip(" .") != part or
                    invalid.search(part) or reserved.match(part)):
                return True
    return False


def ambiguous_jar_members(names: list[str]) -> bool:
    """Detect paths that collide after Windows case/slash normalization.

    This is advisory only: Java ZIP readers can be case-sensitive, but
    extraction or antivirus tooling on Windows may treat these as one file.
    """
    normalized = [name.replace("\\", "/").casefold() for name in names]
    return len(normalized) != len(set(normalized))


def suspicious_jar_compression(infos: list[zipfile.ZipInfo]) -> bool:
    """Flag unusually large/unbalanced JAR metadata without decompressing it.

    This is an advisory warning, not proof of a malicious archive.
    """
    total = 0
    for info in infos:
        if info.is_dir():
            continue
        total += info.file_size
        if total > 512 * 1024 * 1024:
            return True
        if (info.file_size >= 1024 * 1024 and
                info.file_size > max(info.compress_size, 1) * 1000):
            return True
    return False


def unsupported_jar_compression(infos: list[zipfile.ZipInfo]) -> bool:
    """Java Forge expects stored or DEFLATE entries, not BZIP2/LZMA."""
    return any(info.compress_type not in (zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED)
               for info in infos if not info.is_dir())


def encrypted_jar_members(infos: list[zipfile.ZipInfo]) -> bool:
    """Encrypted ZIP entries cannot be loaded as ordinary Forge JAR assets."""
    return any(info.flag_bits & 0x1 for info in infos if not info.is_dir())


def inspect_instance(folder: Path) -> dict:
    """Read-only inspection of a Minecraft directory. Never writes to disk."""
    # A symlinked instance root may escape the directory the user selected.
    # Do not inspect an external Minecraft tree implicitly.
    if folder.is_symlink():
        return {"exists": True, "linked_instance": True, "forge": False,
                "versions": [], "mods": [],
                "message": "Папка Minecraft является ссылкой: внешние файлы не проверялись."}
    if not folder.is_dir():
        return {"exists": False, "forge": False, "versions": [], "mods": [], "disabled_mods": [],
                "empty_mods": [], "invalid_mods": [], "fabric_mods": [], "neoforge_mods": [], "quilt_mods": [], "unsafe_mods": [], "unknown_mods": [], "large_mods": [], "unsupported_compression_mods": [], "duplicate_members": [], "encrypted_mods": [], "duplicates": [],
                "message": "Папка Minecraft не найдена."}
    try:
        versions_dir = folder / "versions"
        # Do not traverse linked version folders outside the selected instance.
        versions = sorted(p.name for p in versions_dir.iterdir()
                          if p.is_dir() and not p.is_symlink()) if versions_dir.is_dir() and not versions_dir.is_symlink() else []
        mods_dir = folder / "mods"
        linked_mods = mods_dir.is_symlink()
        mods_folder_exists = mods_dir.is_dir() and not linked_mods
        mods = []
        disabled_mods = disabled_mod_names(mods_dir) if mods_folder_exists else []
        # Linked folders/files may point outside .minecraft and must not be
        # inspected without the user's explicit selection of that location.
        if mods_dir.is_dir() and not mods_dir.is_symlink():
            mods.extend(p.name for p in mods_dir.iterdir()
                        if p.is_file() and not p.is_symlink() and p.suffix.lower() == ".jar")
            # Some mod managers keep Forge 1.20.1 JARs in a version folder.
            # Inspect it without changing the user's mod installation.
            versioned = mods_dir / "1.20.1"
            if versioned.is_dir() and not versioned.is_symlink():
                mods.extend("1.20.1/" + p.name for p in versioned.iterdir()
                            if p.is_file() and not p.is_symlink() and p.suffix.lower() == ".jar")
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
        quilt_mods = []
        unsafe_mods = []
        unknown_mods = []
        large_mods = []
        unsupported_compression_mods = []
        duplicate_members = []
        encrypted_mods = []
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
                    member_names = jar.namelist()
                    members = set(member_names)
                    # Duplicate ZIP paths can make different readers choose
                    # different payloads. Warn without extracting or editing.
                    if ambiguous_jar_members(member_names):
                        duplicate_members.append(name)
                    if suspicious_jar_compression(jar.infolist()):
                        large_mods.append(name)
                    if unsupported_jar_compression(jar.infolist()):
                        unsupported_compression_mods.append(name)
                    if encrypted_jar_members(jar.infolist()):
                        encrypted_mods.append(name)
                    if unsafe_jar_members(list(members)):
                        unsafe_mods.append(name)
                    if "fabric.mod.json" in members and "META-INF/mods.toml" not in members:
                        fabric_mods.append(name)
                    # NeoForge uses a different mod descriptor. Warn only when
                    # the Forge descriptor is absent; do not edit any JAR.
                    if "META-INF/neoforge.mods.toml" in members and "META-INF/mods.toml" not in members:
                        neoforge_mods.append(name)
                    if "quilt.mod.json" in members and "META-INF/mods.toml" not in members:
                        quilt_mods.append(name)
                    # A readable ZIP is not necessarily a Forge mod. Keep this
                    # advisory only: legacy coremods may use other descriptors.
                    descriptors = {"META-INF/mods.toml", "fabric.mod.json",
                                   "quilt.mod.json", "META-INF/neoforge.mods.toml",
                                   "mcmod.info"}
                    if not members.intersection(descriptors):
                        unknown_mods.append(name)
            except zipfile.BadZipFile:
                invalid_mods.append(name)
    except OSError:
        return {"exists": True, "forge": False, "versions": [], "mods": [], "disabled_mods": [],
                "empty_mods": [], "invalid_mods": [], "fabric_mods": [], "neoforge_mods": [], "quilt_mods": [], "unsafe_mods": [], "unknown_mods": [], "large_mods": [], "unsupported_compression_mods": [], "duplicate_members": [], "encrypted_mods": [], "duplicates": [],
                "message": "Не удалось прочитать файлы Minecraft (нет доступа или ошибка диска)."}
    forge = bool(forge_1201_versions(versions))
    message = ("Forge 1.20.1 обнаружен" if forge else
               "Forge 1.20.1 не найден среди установленных версий")
    return {"exists": True, "forge": forge, "versions": versions,
            "mods": mods, "disabled_mods": disabled_mods,
            "mods_folder_exists": mods_folder_exists,
            "linked_mods": linked_mods,
            "duplicates": duplicate_mod_names(mods),
            "empty_mods": empty_mods, "invalid_mods": invalid_mods,
            "fabric_mods": fabric_mods, "neoforge_mods": neoforge_mods, "quilt_mods": quilt_mods, "unsafe_mods": unsafe_mods, "unknown_mods": unknown_mods, "large_mods": large_mods, "unsupported_compression_mods": unsupported_compression_mods, "duplicate_members": duplicate_members, "encrypted_mods": encrypted_mods,
            "message": message}


def format_instance_report(report: dict) -> str:
    """Share a local, read-only diagnostic summary without absolute paths."""
    lines = ["Shadow RP — диагностика Forge 1.20.1", report["message"]]
    if not report.get("exists") or report.get("linked_instance"):
        return "\n".join(lines)
    lines.append(f"Версий Minecraft: {len(report['versions'])}")
    matching = forge_1201_versions(report['versions'])
    if matching:
        lines.append("Forge 1.20.1: " + ", ".join(matching))
    lines.append(f"JAR-модов: {len(report['mods'])}")
    if report.get("disabled_mods"):
        lines.append("Отключённые JAR (не загружаются Forge): " +
                     ", ".join(report["disabled_mods"]))
    if not report['mods']:
        if report.get("linked_mods"):
            lines.append("Папка mods является ссылкой: внешний каталог не проверялся")
        elif not report.get("mods_folder_exists"):
            lines.append("Папка mods отсутствует: проверь выбранную установку Minecraft")
        else:
            lines.append("Моды не найдены: проверь папку mods и mods/1.20.1")
    warnings = (
        ("duplicates", "Повторяющиеся JAR"),
        ("empty_mods", "Пустые JAR"),
        ("invalid_mods", "Повреждённые JAR"),
        ("fabric_mods", "Возможно несовместимые Fabric"),
        ("neoforge_mods", "Возможно несовместимые NeoForge"),
        ("quilt_mods", "Возможно несовместимые Quilt"),
        ("unsafe_mods", "Подозрительные пути внутри JAR"),
        ("unknown_mods", "JAR без распознанных метаданных мода (проверь вручную)"),
        ("large_mods", "Подозрительно большой или сильно сжатый JAR (проверь вручную)"),
        ("unsupported_compression_mods", "JAR с неподдерживаемым Java сжатием (BZIP2/LZMA)"),
        ("duplicate_members", "Повторяющиеся или неоднозначные пути внутри JAR (проверь вручную)"),
        ("encrypted_mods", "Зашифрованные записи JAR несовместимы с обычной загрузкой Forge"),
    )
    for key, label in warnings:
        if report.get(key):
            lines.append(f"{label}: {', '.join(report[key])}")
    return "\n".join(lines)


def save_report_text(destination: Path, report: dict) -> None:
    """Write only the sanitized summary to a user-chosen local text file."""
    destination.write_text(format_instance_report(report) + "\n", encoding="utf-8")


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
        self._button(controls, "Скопировать отчёт", self.copy_report).pack(side="left", padx=4)
        self._button(controls, "Сохранить .txt", self.save_report).pack(side="left", padx=4)
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
        self.last_report = report
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
            + ("\nВозможно несовместимые Quilt-моды для Forge: " + ", ".join(report["quilt_mods"])
               if report.get("quilt_mods") else "")
            + ("\nJAR без распознанных метаданных мода: " + ", ".join(report["unknown_mods"])
               if report.get("unknown_mods") else "")
            + ("\nПроверь размер/сжатие JAR: " + ", ".join(report["large_mods"])
               if report.get("large_mods") else "")
        ))
        self.mods.delete(0, tk.END)
        for name in report["mods"]:
            self.mods.insert(tk.END, name)

    def copy_report(self):
        """Copy only the diagnostic summary; never upload local mod data."""
        self.clipboard_clear()
        self.clipboard_append(format_instance_report(self.last_report))
        self.status.config(text="Отчёт скопирован в буфер обмена")

    def save_report(self):
        """Only write a report when the user explicitly chooses a destination."""
        destination = filedialog.asksaveasfilename(
            title="Сохранить отчёт Shadow RP",
            defaultextension=".txt",
            filetypes=[("Текстовый отчёт", "*.txt")],
            initialfile="shadow-rp-diagnostics.txt",
        )
        if not destination:
            return
        try:
            save_report_text(Path(destination), self.last_report)
        except OSError as exc:
            messagebox.showerror("Shadow RP", f"Не удалось сохранить отчёт: {exc}")
            return
        self.status.config(text="Диагностический отчёт сохранён локально")

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
