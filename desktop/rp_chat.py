"""Shadow RP desktop: local character chats and optional OpenAI-compatible AI."""
import os
import threading
import tkinter as tk
from pathlib import Path
from tkinter import filedialog, messagebox, simpledialog, scrolledtext
from rp_backup import encode_backup, decode_backup, merge_backups, export_character, MAX_BYTES
from rp_storage import load_data, save_data
from rp_context import build_rp_messages, validate_endpoint
from rp_memory import pin_last_exchange
from rp_history import search_dialogue, rewind_to_user_turn
from rp_reply_safety import card_context, can_apply_reply
from rp_transport import chat_completion

DATA = Path(os.environ.get("APPDATA", str(Path.home()))) / "ShadowRP" / "chats.json"
BG, PANEL, FG, BLUE = "#080f20", "#172640", "#edf5ff", "#60baff"


def load():
    return load_data(DATA)[0]


def save(data):
    save_data(DATA, data)


def generate(endpoint, api_key, model, description, messages, scenario="", memory="",
             *, appearance="", greeting="", protocol=""):
    base = validate_endpoint(endpoint)
    return chat_completion(
        base, api_key, model,
        build_rp_messages(description, scenario, memory, messages,
                          appearance=appearance, greeting=greeting, protocol=protocol))


class App:
    def __init__(self, root):
        self.root = root
        self.data, self.recovered_backup = load_data(DATA)
        self.pending = 0
        self.active = next(iter(self.data["characters"]), None)
        root.title("Shadow RP — ИИ-ролевой чат")
        root.geometry("1050x700")
        root.configure(bg=BG)
        root.minsize(760, 520)
        # Shared dark-blue visual language for every actual Windows button,
        # including dynamically created character and history controls.
        root.option_add("*Button.background", "#20385E")
        root.option_add("*Button.foreground", FG)
        root.option_add("*Button.activeBackground", "#345C93")
        root.option_add("*Button.activeForeground", "#FFFFFF")
        root.option_add("*Button.relief", "flat")
        root.option_add("*Button.borderWidth", 0)
        root.option_add("*Button.font", ("Segoe UI", 10, "bold"))
        root.option_add("*Button.cursor", "hand2")
        sidebar = tk.Frame(root, bg=PANEL, width=235)
        sidebar.pack(side="left", fill="y")
        tk.Label(sidebar, text="SHADOW RP", bg=PANEL, fg=BLUE, font=("Segoe UI", 18, "bold")).pack(pady=18)
        self.characters = tk.Listbox(sidebar, bg=BG, fg=FG, selectbackground="#23539a", borderwidth=0, font=("Segoe UI", 12))
        self.characters.pack(fill="both", expand=True, padx=10)
        self.characters.bind("<<ListboxSelect>>", self.select)
        tk.Button(sidebar, text="+ Персонаж", command=self.add).pack(fill="x", padx=10, pady=5)
        tk.Button(sidebar, text="Описание персонажа", command=self.edit).pack(fill="x", padx=10, pady=5)
        tk.Button(sidebar, text="Внешность", command=lambda: self.edit_field("appearance", "Внешность", "Внешность, одежда, особенности:")).pack(fill="x", padx=10, pady=5)
        tk.Button(sidebar, text="Приветствие", command=lambda: self.edit_field("greeting", "Приветствие", "Первая реплика персонажа:")).pack(fill="x", padx=10, pady=5)
        tk.Button(sidebar, text="Правила RP", command=lambda: self.edit_field("protocol", "Правила RP", "Стиль речи и правила поведения персонажа:")).pack(fill="x", padx=10, pady=5)
        tk.Button(sidebar, text="Сценарий", command=lambda: self.edit_field("scenario", "Сценарий", "Мир, ситуация, сюжет:")).pack(fill="x", padx=10, pady=5)
        tk.Button(sidebar, text="Закреплённая память", command=lambda: self.edit_field("memory", "Память", "Подтверждённые факты и события:")).pack(fill="x", padx=10, pady=5)
        tk.Button(sidebar, text="📌 Запомнить сцену", command=self.pin_scene).pack(fill="x", padx=10, pady=5)
        tk.Button(sidebar, text="Поиск и откат диалога", command=self.history_tools).pack(fill="x", padx=10, pady=5)
        tk.Button(sidebar, text="Экспорт персонажа", command=self.export_active_character).pack(fill="x", padx=10, pady=5)
        tk.Button(sidebar, text="Экспорт чатов", command=self.export_chats).pack(fill="x", padx=10, pady=5)
        tk.Button(sidebar, text="Импорт чатов", command=self.import_chats).pack(fill="x", padx=10, pady=5)
        main = tk.Frame(root, bg=BG)
        main.pack(side="left", fill="both", expand=True, padx=14, pady=14)
        self.title = tk.Label(main, bg=BG, fg=FG, font=("Segoe UI", 17, "bold"))
        self.title.pack(anchor="w")
        self.history = scrolledtext.ScrolledText(main, bg=PANEL, fg=FG, wrap="word", state="disabled", font=("Segoe UI", 11), borderwidth=0)
        self.history.pack(fill="both", expand=True, pady=10)
        self.history.tag_configure("player_name",
            foreground="#85CAFF", font=("Segoe UI", 11, "bold"))
        self.history.tag_configure("character_name",
            foreground="#C2B0FF", font=("Segoe UI", 11, "bold"))
        self.history.tag_configure("message", foreground=FG,
            spacing3=10)
        config = tk.Frame(main, bg=BG)
        config.pack(fill="x")
        self.endpoint = tk.Entry(config, width=35)
        self.endpoint.insert(0, os.environ.get("SHADOWRP_API_BASE", "http://localhost:1234/v1"))
        self.endpoint.pack(side="left", padx=3)
        self.model = tk.Entry(config, width=18)
        self.model.insert(0, os.environ.get("SHADOWRP_MODEL", "local-model"))
        self.model.pack(side="left", padx=3)
        self.key = tk.Entry(config, show="*", width=16)
        self.key.insert(0, os.environ.get("SHADOWRP_API_KEY", ""))
        self.key.pack(side="left", padx=3)
        tk.Label(main, text="API base URL  |  Model  |  API key (не сохраняется)", bg=BG, fg=BLUE).pack(anchor="w")
        bottom = tk.Frame(main, bg=BG)
        bottom.pack(fill="x", pady=8)
        self.entry = tk.Entry(bottom, bg=PANEL, fg=FG, insertbackground=FG, font=("Segoe UI", 12))
        self.entry.pack(side="left", fill="x", expand=True)
        self.entry.bind("<Return>", lambda _: self.send())
        self.button = tk.Button(bottom, text="Отправить ИИ", command=self.send)
        self.button.pack(side="left", padx=6)
        self.retry_button = tk.Button(bottom, text="Повторить ответ", command=self.retry_last, state="disabled")
        self.retry_button.pack(side="left", padx=3)
        self.status = tk.Label(main, text="Подключи совместимый API или локальную модель", bg=BG, fg=BLUE)
        self.status.pack(anchor="w")
        if self.recovered_backup:
            self.status.config(text="Восстановлено из резервной копии: основной файл повреждён")
        self.refresh()

    def export_active_character(self):
        """Share only the selected character, without other private chats."""
        if not self.active:
            messagebox.showwarning("Экспорт", "Сначала выбери персонажа.", parent=self.root)
            return
        path = filedialog.asksaveasfilename(
            parent=self.root, title="Экспорт персонажа Shadow RP",
            defaultextension=".json", initialfile="shadow-rp-character.json",
            filetypes=[("JSON", "*.json")])
        if not path:
            return
        try:
            # Validate before writing: failed exports must not truncate files.
            raw = export_character(self.data, self.active)
            Path(path).write_bytes(raw)
            self.status.config(text="Персонаж экспортирован для Android/Windows")
        except (OSError, ValueError) as exc:
            messagebox.showerror("Ошибка экспорта", str(exc), parent=self.root)

    def export_chats(self):
        path = filedialog.asksaveasfilename(
            parent=self.root, title="Экспорт Shadow RP",
            defaultextension=".json", initialfile="shadow-rp-backup.json",
            filetypes=[("JSON", "*.json")])
        if not path:
            return
        try:
            Path(path).write_bytes(encode_backup(self.data))
            self.status.config(text="Архив чатов сохранён")
        except (OSError, ValueError) as exc:
            messagebox.showerror("Ошибка экспорта", str(exc))

    def import_chats(self):
        if self.pending:
            messagebox.showwarning("Импорт", "Дождись ответа ИИ перед импортом.")
            return
        path = filedialog.askopenfilename(
            parent=self.root, title="Импорт Shadow RP",
            filetypes=[("JSON", "*.json")])
        if not path:
            return
        try:
            with open(path, "rb") as source:
                raw = source.read(MAX_BYTES + 1)
            imported = decode_backup(raw)
            merged = merge_backups(self.data, imported)
            count = len(imported["characters"])
            collisions = len(set(imported["characters"]) & set(self.data["characters"]))
            if not messagebox.askyesno(
                "Импорт", f"Импортировать {count} персонажей? Совпадающих имён: "
                f"{collisions}. Их карточки и истории будут заменены, остальные "
                "персонажи останутся. Рекомендуется сначала экспортировать данные.",
                parent=self.root):
                return
            save(merged)
            self.data = merged
            self.active = self.active if self.active in merged["characters"] else next(iter(merged["characters"]), None)
            self.refresh()
            self.status.config(text="Чаты импортированы; остальные сохранены")
        except (OSError, ValueError, UnicodeError) as exc:
            messagebox.showerror("Ошибка импорта", str(exc))

    def refresh(self):
        names = list(self.data["characters"])
        self.characters.delete(0, "end")
        for name in names:
            self.characters.insert("end", name)
        if self.active in names:
            self.characters.selection_set(names.index(self.active))
        self.title.config(text=self.active or "Нет персонажей")
        self.history.config(state="normal")
        self.history.delete("1.0", "end")
        if self.active:
            card = self.data["characters"][self.active]
            if not card["messages"] and card.get("greeting", "").strip():
                self.history.insert("end", f"{self.active}: ", "character_name")
                self.history.insert("end", f"{card['greeting']}\n\n", "message")
            for msg in card["messages"]:
                speaker = "Ты" if msg["role"] == "user" else self.active
                name_tag = "player_name" if msg["role"] == "user" else "character_name"
                self.history.insert("end", f"{speaker}: ", name_tag)
                self.history.insert("end", f"{msg['content']}\n\n", "message")
        self.history.config(state="disabled")
        self.history.see("end")
        # A failed request leaves the user turn intact, so retry it without
        # creating a duplicate message or losing character continuity.
        item = self.data["characters"].get(self.active) if self.active else None
        last = item["messages"][-1] if item and item["messages"] else None
        self.retry_button.config(state="normal" if not self.pending and
                                 last and last["role"] == "user" else "disabled")

    def select(self, _):
        selection = self.characters.curselection()
        if selection:
            self.active = self.characters.get(selection[0])
            self.refresh()

    def add(self):
        name = simpledialog.askstring("Новый персонаж", "Имя персонажа:", parent=self.root)
        if name and name.strip():
            name = name.strip()
            if name in self.data["characters"]:
                messagebox.showwarning("Имя занято", "Такой персонаж уже существует")
                return
            self.data["characters"][name] = {"description": "", "scenario": "", "memory": "", "messages": []}
            self.active = name
            save(self.data)
            self.refresh()

    def edit(self):
        if not self.active:
            return
        self.edit_field("description", "Карточка персонажа",
                        "Характер, мир, правила RP:")

    def edit_field(self, field, title, prompt):
        if not self.active:
            return
        item = self.data["characters"][self.active]
        current = item.get(field, "")
        value = simpledialog.askstring(title, prompt,
                                       initialvalue=current, parent=self.root)
        if value is not None:
            item[field] = value[:4000]
            save(self.data)
            self.status.config(text="Сохранено локально. Память меняется только вручную.")

    def pin_scene(self):
        """Only the player's explicit approval can change long-term memory."""
        if self.pending or not self.active:
            messagebox.showwarning("Память", "Дождись ответа ИИ и выбери персонажа.")
            return
        item = self.data["characters"][self.active]
        try:
            proposed = pin_last_exchange(item.get("memory", ""), item["messages"])
        except ValueError as exc:
            messagebox.showwarning("Память", str(exc))
            return
        if proposed == item.get("memory", ""):
            self.status.config(text="Эта сцена уже закреплена")
            return
        if not messagebox.askyesno(
            "Закрепить сцену?",
            "Добавить последние реплики игрока и персонажа в долговременную "
            "память? Текст будет передаваться модели в следующих диалогах и "
            "включаться в ручной экспорт.\n\n" + proposed[-800:],
            parent=self.root,
        ):
            return
        item["memory"] = proposed
        save(self.data)
        self.status.config(text="Сцена закреплена вручную; память сохранена")


    def history_tools(self):
        """Search all turns; rewind only after explicit confirmation and backup."""
        if not self.active or self.pending:
            messagebox.showwarning("История", "Выбери персонажа и дождись ответа ИИ.")
            return
        name = self.active
        dialog = tk.Toplevel(self.root)
        dialog.title(f"История — {name}")
        dialog.geometry("700x520")
        dialog.configure(bg=BG)
        dialog.transient(self.root)
        tk.Label(dialog, text="Поиск по всей переписке (до 50 совпадений)",
                 bg=BG, fg=FG).pack(anchor="w", padx=12, pady=8)
        query = tk.StringVar()
        field = tk.Entry(dialog, textvariable=query, bg=PANEL, fg=FG,
                         insertbackground=FG)
        field.pack(fill="x", padx=12)
        listing = tk.Listbox(dialog, bg=PANEL, fg=FG, selectbackground="#23539a",
                             font=("Segoe UI", 10))
        listing.pack(fill="both", expand=True, padx=12, pady=8)
        matches = []

        def populate(*_):
            nonlocal matches
            card = self.data["characters"].get(name)
            if card is None:
                matches = []
            else:
                matches = search_dialogue(card["messages"], query.get()[:200])
            listing.delete(0, "end")
            for index, role, preview in matches:
                speaker = "Ты" if role == "user" else name
                listing.insert("end", f"#{index + 1} {speaker}: {preview}")

        def rewind():
            selected = listing.curselection()
            if not selected or self.pending:
                messagebox.showwarning("История", "Выбери реплику и дождись ответа ИИ.",
                                       parent=dialog)
                return
            index, role, _ = matches[selected[0]]
            if role != "user":
                messagebox.showwarning("История", "Для отката выбери реплику игрока.",
                                       parent=dialog)
                return
            try:
                candidate = rewind_to_user_turn(self.data, name, index)
            except ValueError as exc:
                messagebox.showwarning("История", str(exc), parent=dialog)
                return
            removed = (len(self.data["characters"][name]["messages"]) -
                       len(candidate["characters"][name]["messages"]))
            if not messagebox.askyesno(
                "Откат диалога",
                f"Удалить {removed} последующих реплик персонажа «{name}»?\\n"
                "Перед откатом будет создан отдельный архив "
                "chats-rewind-checkpoint.json. Его можно импортировать обратно.",
                parent=dialog,
            ):
                return
            try:
                # Do not overwrite the live conversation until the complete
                # pre-rewind archive has been safely written to disk.
                save_data(DATA.with_name("chats-rewind-checkpoint.json"), self.data)
                save(candidate)
            except (OSError, ValueError) as exc:
                messagebox.showerror("Ошибка сохранения", str(exc), parent=dialog)
                return
            self.data = candidate
            self.active = name
            self.refresh()
            self.status.config(text=f"Откат: удалено {removed} реплик; архив сохранён")
            dialog.destroy()

        query.trace_add("write", populate)
        tk.Button(dialog, text="Откатить к выбранной реплике игрока",
                  command=rewind).pack(fill="x", padx=12, pady=(0, 12))
        populate()
        field.focus_set()

    def send(self):
        if self.pending:
            return
        content = self.entry.get().strip()
        if not content or not self.active:
            return
        name = self.active
        item = self.data["characters"][name]
        item["messages"].append({"role": "user", "content": content})
        save(self.data)
        self.entry.delete(0, "end")
        self._request_reply(name, item)

    def retry_last(self):
        if self.pending or not self.active:
            return
        name = self.active
        item = self.data["characters"][name]
        if not item["messages"] or item["messages"][-1]["role"] != "user":
            return
        self._request_reply(name, item)

    def _request_reply(self, name, item):
        self.pending += 1
        self.button.config(state="disabled")
        self.retry_button.config(state="disabled")
        self.status.config(text="ИИ отвечает…")
        self.refresh()
        endpoint, model, key = self.endpoint.get().strip(), self.model.get().strip(), self.key.get()
        snapshot = [dict(message) for message in item["messages"]]
        expected_context = card_context(item)
        description = item.get("description", "")
        scenario = item.get("scenario", "")
        memory = item.get("memory", "")
        appearance = item.get("appearance", "")
        greeting = item.get("greeting", "")
        protocol = item.get("protocol", "")

        def work():
            try:
                answer = generate(endpoint, key, model, description, snapshot,
                                  scenario, memory, appearance=appearance,
                                  greeting=greeting, protocol=protocol)
                error = None
            except Exception as exc:
                answer, error = None, str(exc)

            def finish():
                current = self.data["characters"].get(name)
                applicable = (current is item and
                              can_apply_reply(snapshot, current.get("messages"),
                                              expected_context, card_context(current)))
                if answer and applicable:
                    current["messages"].append({"role": "assistant", "content": answer})
                    save(self.data)
                self.pending = max(0, self.pending - 1)
                self.button.config(state="normal")
                self.status.config(text=("Готово" if answer and applicable else
                    "Ответ устарел — история или карточка изменилась" if answer else
                    "Ошибка подключения — сообщение сохранено, можно повторить"))
                if error:
                    messagebox.showerror("ИИ недоступен", error)
                self.refresh()
            self.root.after(0, finish)
        threading.Thread(target=work, daemon=True).start()


if __name__ == "__main__":
    root = tk.Tk()
    App(root)
    root.mainloop()
