"""Shadow RP desktop: local character chats and optional OpenAI-compatible AI."""
import json
import os
import threading
import tkinter as tk
from pathlib import Path
from tkinter import messagebox, simpledialog, scrolledtext
from urllib.request import Request, HTTPRedirectHandler, build_opener
from rp_context import build_rp_messages, validate_endpoint

DATA = Path(os.environ.get("APPDATA", str(Path.home()))) / "ShadowRP" / "chats.json"
BG, PANEL, FG, BLUE = "#080f20", "#172640", "#edf5ff", "#60baff"


def load():
    try:
        data = json.loads(DATA.read_text(encoding="utf-8"))
        if isinstance(data, dict) and isinstance(data.get("characters"), dict):
            return data
    except (OSError, ValueError, TypeError):
        pass
    return {"characters": {"Новый персонаж": {"description": "", "messages": []}}}


def save(data):
    DATA.parent.mkdir(parents=True, exist_ok=True)
    temp = DATA.with_suffix(".tmp")
    temp.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    temp.replace(DATA)


class _NoRedirect(HTTPRedirectHandler):
    """Never forward an API key to an unexpected redirected host."""
    def redirect_request(self, request, fp, code, msg, headers, newurl):
        return None


def generate(endpoint, api_key, model, description, messages, scenario="", memory=""):
    base = validate_endpoint(endpoint)
    if not model.strip() or len(model) > 120:
        raise ValueError("Укажи корректное имя модели")
    url = base + "/chat/completions"
    payload = {
        "model": model.strip(),
        "messages": build_rp_messages(description, scenario, memory, messages),
    }
    headers = {"Content-Type": "application/json"}
    if api_key:
        headers["Authorization"] = "Bearer " + api_key
    req = Request(url, data=json.dumps(payload).encode("utf-8"),
                  headers=headers, method="POST")
    # No redirects: a 302 must not leak the bearer token to another host.
    opener = build_opener(_NoRedirect())
    with opener.open(req, timeout=45) as response:
        raw = response.read(2 * 1024 * 1024 + 1)
    if len(raw) > 2 * 1024 * 1024:
        raise ValueError("Ответ модели превышает 2 МБ")
    result = json.loads(raw)
    answer = result["choices"][0]["message"]["content"]
    if not isinstance(answer, str) or not answer.strip():
        raise ValueError("Модель вернула пустой ответ")
    return answer[:20000]


class App:
    def __init__(self, root):
        self.root = root
        self.data = load()
        self.active = next(iter(self.data["characters"]), None)
        root.title("Shadow RP — ИИ-ролевой чат")
        root.geometry("1050x700")
        root.configure(bg=BG)
        sidebar = tk.Frame(root, bg=PANEL, width=235)
        sidebar.pack(side="left", fill="y")
        tk.Label(sidebar, text="SHADOW RP", bg=PANEL, fg=BLUE, font=("Segoe UI", 18, "bold")).pack(pady=18)
        self.characters = tk.Listbox(sidebar, bg=BG, fg=FG, selectbackground="#23539a", borderwidth=0, font=("Segoe UI", 12))
        self.characters.pack(fill="both", expand=True, padx=10)
        self.characters.bind("<<ListboxSelect>>", self.select)
        tk.Button(sidebar, text="+ Персонаж", command=self.add).pack(fill="x", padx=10, pady=5)
        tk.Button(sidebar, text="Описание персонажа", command=self.edit).pack(fill="x", padx=10, pady=5)
        tk.Button(sidebar, text="Сценарий", command=lambda: self.edit_field("scenario", "Сценарий", "Мир, ситуация, сюжет:")).pack(fill="x", padx=10, pady=5)
        tk.Button(sidebar, text="Закреплённая память", command=lambda: self.edit_field("memory", "Память", "Подтверждённые факты и события:")).pack(fill="x", padx=10, pady=5)
        main = tk.Frame(root, bg=BG)
        main.pack(side="left", fill="both", expand=True, padx=14, pady=14)
        self.title = tk.Label(main, bg=BG, fg=FG, font=("Segoe UI", 17, "bold"))
        self.title.pack(anchor="w")
        self.history = scrolledtext.ScrolledText(main, bg=PANEL, fg=FG, wrap="word", state="disabled", font=("Segoe UI", 11), borderwidth=0)
        self.history.pack(fill="both", expand=True, pady=10)
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
        self.status = tk.Label(main, text="Подключи совместимый API или локальную модель", bg=BG, fg=BLUE)
        self.status.pack(anchor="w")
        self.refresh()

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
            for msg in self.data["characters"][self.active]["messages"]:
                speaker = "Ты" if msg["role"] == "user" else self.active
                self.history.insert("end", f"{speaker}: {msg['content']}\n\n")
        self.history.config(state="disabled")
        self.history.see("end")

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
        item = self.data["characters"][self.active]
        description = simpledialog.askstring("Карточка персонажа", "Характер, мир, правила RP:", initialvalue=item["description"], parent=self.root)
        if description is not None:
            item["description"] = description
            save(self.data)

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

    def send(self):
        content = self.entry.get().strip()
        if not content or not self.active:
            return
        name = self.active
        item = self.data["characters"][name]
        item["messages"].append({"role": "user", "content": content})
        save(self.data)
        self.entry.delete(0, "end")
        self.refresh()
        self.button.config(state="disabled")
        self.status.config(text="ИИ отвечает…")
        endpoint, model, key = self.endpoint.get().strip(), self.model.get().strip(), self.key.get()
        snapshot = list(item["messages"])
        def work():
            try:
                answer = generate(endpoint, key, model, item.get("description", ""),
                                  snapshot, item.get("scenario", ""),
                                  item.get("memory", ""))
                error = None
            except Exception as exc:
                answer, error = None, str(exc)
            def finish():
                if answer:
                    item["messages"].append({"role": "assistant", "content": answer})
                    save(self.data)
                self.button.config(state="normal")
                self.status.config(text="Готово" if answer else "Ошибка подключения — сообщение сохранено")
                if error:
                    messagebox.showerror("ИИ недоступен", error)
                self.refresh()
            self.root.after(0, finish)
        threading.Thread(target=work, daemon=True).start()


if __name__ == "__main__":
    root = tk.Tk()
    App(root)
    root.mainloop()
