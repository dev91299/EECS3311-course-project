#!/usr/bin/env python3
"""Generates the use-case and sequence diagrams for the Stage 2 report as SVG.

The diagrams are generated from this script rather than drawn by hand so that they can be
regenerated whenever the implementation changes, and so that the report and the code cannot
drift apart silently.

Usage:
    python3 generate_diagrams.py            # writes .svg files next to this script
    ./render.sh                             # also converts them to .png

No third-party packages are required.
"""

import os

OUT_DIR = os.path.dirname(os.path.abspath(__file__))

FONT = "Helvetica, Arial, sans-serif"
MONO = "Menlo, Consolas, monospace"

INK = "#1C1F23"
MUTED = "#5B6570"
LINE = "#8A93A0"
ACCENT = "#2D6CDF"
NEW = "#B35309"
BOX_FILL = "#EAF0FA"
NEW_FILL = "#FDF1E3"


def esc(text):
    return (text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"))


# --------------------------------------------------------------------------- use case


def use_case_diagram():
    width, height = 1180, 720
    out = [header(width, height)]

    out.append(f'<text x="{width/2}" y="34" text-anchor="middle" font-family="{FONT}" '
               f'font-size="19" font-weight="700" fill="{INK}">Use-Case Diagram - '
               f'AI Code Review Agent (Stage 2, implemented)</text>')

    # System boundary
    out.append(f'<rect x="250" y="62" width="680" height="612" rx="8" fill="none" '
               f'stroke="{LINE}" stroke-width="1.6"/>')
    out.append(f'<text x="268" y="88" font-family="{FONT}" font-size="13" font-weight="700" '
               f'fill="{MUTED}">System: AI Code Review Agent</text>')

    use_cases = [
        ("UC01", "Import &amp; Browse", "Repository", "F01", 420, 150, False),
        ("UC02", "Perform Code", "Analysis", "F02, F03, F06", 760, 150, False),
        ("UC03", "Generate Code", "Modifications", "F04, F05", 420, 285, False),
        ("UC04", "Manage Review", "Findings", "F07, F08, F09, F12", 760, 285, False),
        ("UC05", "Query AI", "Assistant", "F10", 420, 420, False),
        ("UC06", "Run Autonomous", "Audit", "F11", 760, 420, True),
        ("UC07", "Search Codebase", "(retrieval)", "F13", 590, 570, True),
    ]

    positions = {}
    for code, line1, line2, features, cx, cy, is_new in use_cases:
        positions[code] = (cx, cy)
        fill = NEW_FILL if is_new else BOX_FILL
        stroke = NEW if is_new else ACCENT
        out.append(f'<ellipse cx="{cx}" cy="{cy}" rx="128" ry="52" fill="{fill}" '
                   f'stroke="{stroke}" stroke-width="1.6"/>')
        out.append(f'<text x="{cx}" y="{cy - 16}" text-anchor="middle" font-family="{FONT}" '
                   f'font-size="12" font-weight="700" fill="{INK}">{code}</text>')
        out.append(f'<text x="{cx}" y="{cy + 1}" text-anchor="middle" font-family="{FONT}" '
                   f'font-size="12" fill="{INK}">{line1}</text>')
        out.append(f'<text x="{cx}" y="{cy + 16}" text-anchor="middle" font-family="{FONT}" '
                   f'font-size="12" fill="{INK}">{line2}</text>')
        out.append(f'<text x="{cx}" y="{cy + 33}" text-anchor="middle" font-family="{MONO}" '
                   f'font-size="9.5" fill="{MUTED}">{features}</text>')
        if is_new:
            out.append(f'<text x="{cx}" y="{cy - 36}" text-anchor="middle" font-family="{FONT}" '
                       f'font-size="9.5" font-weight="700" fill="{NEW}">NEW IN STAGE 2</text>')

    # Actors
    out.append(actor(120, 300, "Developer"))
    out.append(actor(1060, 230, "LLM Service"))
    out.append(actor(1060, 500, "Local Git /", "File System"))

    # Developer associations (all seven use cases)
    for code in ["UC01", "UC02", "UC03", "UC04", "UC05", "UC06", "UC07"]:
        cx, cy = positions[code]
        out.append(line(150, 300, cx - 128, cy))

    # LLM Service participates in the AI-backed use cases
    for code in ["UC02", "UC03", "UC05", "UC06"]:
        cx, cy = positions[code]
        out.append(line(1030, 230, cx + 128, cy))

    # File system / Git participates in import, findings comparison and search
    for code in ["UC01", "UC04", "UC07"]:
        cx, cy = positions[code]
        out.append(line(1030, 500, cx + 128, cy))

    # Legend
    out.append(f'<rect x="40" y="600" width="196" height="76" rx="6" fill="#F7F8FA" '
               f'stroke="#DDE2E8"/>')
    out.append(f'<text x="52" y="622" font-family="{FONT}" font-size="11" font-weight="700" '
               f'fill="{MUTED}">Legend</text>')
    out.append(f'<ellipse cx="64" cy="640" rx="11" ry="7" fill="{BOX_FILL}" stroke="{ACCENT}"/>')
    out.append(f'<text x="82" y="644" font-family="{FONT}" font-size="10.5" fill="{INK}">'
               f'From Stage 1</text>')
    out.append(f'<ellipse cx="64" cy="662" rx="11" ry="7" fill="{NEW_FILL}" stroke="{NEW}"/>')
    out.append(f'<text x="82" y="666" font-family="{FONT}" font-size="10.5" fill="{INK}">'
               f'Added in Stage 2</text>')

    out.append("</svg>")
    write("use-case-diagram.svg", "\n".join(out))


def actor(x, y, label, label2=None):
    parts = [
        f'<circle cx="{x}" cy="{y - 44}" r="13" fill="white" stroke="{INK}" stroke-width="1.8"/>',
        f'<line x1="{x}" y1="{y - 31}" x2="{x}" y2="{y + 4}" stroke="{INK}" stroke-width="1.8"/>',
        f'<line x1="{x - 20}" y1="{y - 18}" x2="{x + 20}" y2="{y - 18}" stroke="{INK}" '
        f'stroke-width="1.8"/>',
        f'<line x1="{x}" y1="{y + 4}" x2="{x - 17}" y2="{y + 30}" stroke="{INK}" stroke-width="1.8"/>',
        f'<line x1="{x}" y1="{y + 4}" x2="{x + 17}" y2="{y + 30}" stroke="{INK}" stroke-width="1.8"/>',
        f'<text x="{x}" y="{y + 50}" text-anchor="middle" font-family="{FONT}" font-size="12.5" '
        f'font-weight="700" fill="{INK}">{label}</text>',
    ]
    if label2:
        parts.append(f'<text x="{x}" y="{y + 65}" text-anchor="middle" font-family="{FONT}" '
                     f'font-size="12.5" font-weight="700" fill="{INK}">{label2}</text>')
    return "\n".join(parts)


def line(x1, y1, x2, y2):
    return (f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{LINE}" stroke-width="1.1"/>')


# --------------------------------------------------------------------------- sequence


class Sequence:
    """Builds a UML sequence diagram as SVG."""

    def __init__(self, title, lifelines, width=1320, gap=None, top=130):
        self.title = title
        self.lifelines = lifelines
        self.width = width
        self.left = 100
        count = len(lifelines)
        self.gap = gap or ((width - 2 * self.left) / max(1, count - 1))
        self.top = top
        self.y = top + 30
        self.events = []
        self.frames = []
        self._open_frames = []

    def x(self, index):
        return self.left + index * self.gap

    def msg(self, source, target, label, kind="call", gap=46):
        self.events.append(("msg", source, target, label, kind, self.y))
        self.y += gap

    def self_msg(self, index, label, gap=56):
        self.events.append(("self", index, index, label, "self", self.y))
        self.y += gap

    def note(self, index, text, width=250, gap=52):
        self.events.append(("note", index, index, text, "note", self.y))
        self.y += gap

    def divider(self, label, gap=44):
        self.events.append(("divider", 0, 0, label, "divider", self.y))
        self.y += gap

    def frame_start(self, label):
        self._open_frames.append((label, self.y - 26))
        self.y += 18

    def frame_end(self):
        label, start = self._open_frames.pop()
        self.frames.append((label, start, self.y - 10))
        self.y += 14

    def render(self, filename):
        height = int(self.y + 60)
        out = [header(self.width, height)]

        out.append(f'<text x="{self.width/2}" y="34" text-anchor="middle" font-family="{FONT}" '
                   f'font-size="18" font-weight="700" fill="{INK}">{esc(self.title)}</text>')

        # Frames behind everything else.
        for label, start, end in self.frames:
            out.append(f'<rect x="{self.left - 70}" y="{start}" '
                       f'width="{self.width - 2 * self.left + 140}" height="{end - start}" '
                       f'rx="4" fill="none" stroke="{ACCENT}" stroke-width="1.2" '
                       f'stroke-dasharray="none" opacity="0.85"/>')
            out.append(f'<path d="M {self.left - 70} {start} h 150 l 14 20 v 6 h -164 z" '
                       f'fill="#E8EFFB" stroke="{ACCENT}" stroke-width="1.2"/>')
            out.append(f'<text x="{self.left - 60}" y="{start + 17}" font-family="{FONT}" '
                       f'font-size="11.5" font-weight="700" fill="{ACCENT}">{esc(label)}</text>')

        # Lifelines.
        for index, name in enumerate(self.lifelines):
            cx = self.x(index)
            box_w = min(self.gap - 16, 162)
            is_actor = index == 0
            fill = "#F2F5FA" if is_actor else BOX_FILL
            out.append(f'<rect x="{cx - box_w/2}" y="{self.top - 36}" width="{box_w}" height="34" '
                       f'rx="5" fill="{fill}" stroke="{ACCENT}" stroke-width="1.4"/>')
            label = name if len(name) <= 22 else name[:21] + "."
            out.append(f'<text x="{cx}" y="{self.top - 14}" text-anchor="middle" '
                       f'font-family="{FONT}" font-size="11.5" font-weight="700" '
                       f'fill="{INK}">{esc(label)}</text>')
            out.append(f'<line x1="{cx}" y1="{self.top - 2}" x2="{cx}" y2="{height - 30}" '
                       f'stroke="{LINE}" stroke-width="1.1" stroke-dasharray="6 5"/>')

        # Events.
        for kind_tuple in self.events:
            etype, source, target, label, kind, y = kind_tuple
            if etype == "msg":
                out.append(self._message(source, target, label, kind, y))
            elif etype == "self":
                out.append(self._self_message(source, label, y))
            elif etype == "note":
                out.append(self._note(source, label, y))
            elif etype == "divider":
                out.append(self._divider(label, y))

        out.append("</svg>")
        write(filename, "\n".join(out))

    def _message(self, source, target, label, kind, y):
        x1, x2 = self.x(source), self.x(target)
        dashed = ' stroke-dasharray="7 5"' if kind == "return" else ""
        colour = MUTED if kind == "return" else INK
        head = "url(#open)" if kind == "return" else "url(#solid)"
        direction = 1 if x2 > x1 else -1
        x2adj = x2 - direction * 7
        mid = (x1 + x2) / 2
        lines = str(label).split("\n")
        text = ""
        for i, part in enumerate(lines):
            offset = y - 9 - (len(lines) - 1 - i) * 13
            text += (f'<text x="{mid}" y="{offset}" text-anchor="middle" font-family="{FONT}" '
                     f'font-size="11" fill="{colour}">{esc(part)}</text>')
        return (f'<line x1="{x1}" y1="{y}" x2="{x2adj}" y2="{y}" stroke="{colour}" '
                f'stroke-width="1.4"{dashed} marker-end="{head}"/>' + text)

    def _self_message(self, index, label, y):
        cx = self.x(index)
        lines = str(label).split("\n")
        text_width = max(len(part) for part in lines) * 6.0
        # Flip the loop and its caption to the left when the lifeline is near the right edge,
        # so the text cannot run off the canvas.
        flip = cx + 46 + text_width > self.width - 16
        body = ""
        for i, part in enumerate(lines):
            tx = cx - 46 if flip else cx + 46
            anchor = "end" if flip else "start"
            body += (f'<text x="{tx}" y="{y - 4 + i * 13}" text-anchor="{anchor}" '
                     f'font-family="{FONT}" font-size="11" fill="{INK}">{esc(part)}</text>')
        direction = -1 if flip else 1
        loop = (f'<path d="M {cx} {y - 14} h {32 * direction} v 26 h {-26 * direction}" '
                f'fill="none" stroke="{INK}" stroke-width="1.4" marker-end="url(#solid)"/>')
        return loop + body

    def _note(self, index, text, y):
        cx = self.x(index)
        lines = str(text).split("\n")
        box_w = max(len(part) for part in lines) * 6.0 + 24
        box_h = 15 * len(lines) + 12
        nx = cx + 18
        if nx + box_w > self.width - 20:
            nx = cx - box_w - 18
        body = ""
        for i, part in enumerate(lines):
            body += (f'<text x="{nx + 10}" y="{y - 6 + i * 15}" font-family="{FONT}" '
                     f'font-size="10.5" fill="#6B5A2E">{esc(part)}</text>')
        return (f'<path d="M {nx} {y - 22} h {box_w - 11} l 11 11 v {box_h - 11} h {-box_w} z" '
                f'fill="#FFF8E6" stroke="#D9B45B" stroke-width="1.1"/>' + body)

    def _divider(self, label, y):
        return (f'<line x1="{self.left - 70}" y1="{y - 10}" x2="{self.width - self.left + 70}" '
                f'y2="{y - 10}" stroke="{NEW}" stroke-width="1" stroke-dasharray="4 4"/>'
                f'<rect x="{self.width/2 - 190}" y="{y - 24}" width="380" height="26" rx="4" '
                f'fill="#FDF1E3" stroke="{NEW}" stroke-width="1"/>'
                f'<text x="{self.width/2}" y="{y - 6}" text-anchor="middle" font-family="{FONT}" '
                f'font-size="11.5" font-weight="700" fill="{NEW}">{esc(label)}</text>')


def header(width, height):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" '
            f'viewBox="0 0 {width} {height}">'
            f'<defs>'
            f'<marker id="solid" markerWidth="11" markerHeight="8" refX="10" refY="4" '
            f'orient="auto"><path d="M0,0 L11,4 L0,8 z" fill="{INK}"/></marker>'
            f'<marker id="open" markerWidth="11" markerHeight="8" refX="10" refY="4" '
            f'orient="auto"><path d="M0,0 L11,4 L0,8" fill="none" stroke="{MUTED}" '
            f'stroke-width="1.3"/></marker>'
            f'</defs>'
            f'<rect width="{width}" height="{height}" fill="white"/>')


def write(filename, content):
    path = os.path.join(OUT_DIR, filename)
    with open(path, "w", encoding="utf-8") as handle:
        handle.write(content)
    print("wrote", filename)


# --------------------------------------------------------------------------- SD01


def sd01():
    seq = Sequence(
        "SD01 - Perform Code Analysis (F03) - success flow and API-timeout flow",
        ["Developer", "CodeReviewGUI", "AgentController", "ReviewAgent",
         "AgentMemory", "SecurityStrategy", "LLMClient", "RetryingProvider"],
        width=1460, top=130)

    seq.frame_start("alt  [success]")
    seq.msg(0, 1, 'click "Analyse"')
    seq.msg(1, 2, "requestReview(path, code, \"security\")")
    seq.msg(2, 3, "analyzeCode(path, code, strategy)")
    seq.msg(3, 4, "renderRecall(code, path, 6)")
    seq.msg(4, 3, "remembered team decisions", "return")
    seq.note(3, "Retrieval and memory are gathered BEFORE the model call,\n"
                "so rejected findings are not raised again.")
    seq.msg(3, 5, "executeStrategy(context)")
    seq.self_msg(5, "template: runDeterministicPass()\nthen build prompt (Builder)")
    seq.msg(5, 6, "sendPrompt(system, user, \"security-analysis\")")
    seq.msg(6, 7, "complete(request)")
    seq.msg(7, 6, "JSON findings", "return")
    seq.msg(6, 5, "raw reply text", "return")
    seq.self_msg(5, "parseFindings() + validate()\n(clamp line numbers and severity)")
    seq.msg(5, 3, "List<ReviewItem>", "return")
    seq.msg(3, 2, "findings recorded in ReviewReport", "return")
    seq.msg(2, 1, "updateUI(FINDINGS_ADDED, items)", "return")
    seq.msg(1, 0, "findings table repaints", "return")
    seq.frame_end()

    seq.divider("alt  [API timeout]  -  use case UC02 alternative flow")

    seq.msg(0, 1, 'click "Analyse"')
    seq.msg(1, 2, "requestReview(...)")
    seq.msg(2, 3, "analyzeCode(...)")
    seq.msg(3, 5, "executeStrategy(context)")
    seq.msg(5, 6, "sendPrompt(...)")
    seq.msg(6, 7, "complete(request)")
    seq.self_msg(7, "HttpTimeoutException caught\nretry 1 of 3 after 800 ms")
    seq.msg(7, 1, "updateUI(TASK_STARTED, \"Retrying...\")", "return")
    seq.self_msg(7, "retry 2 of 3 after 1600 ms\nall attempts exhausted")
    seq.msg(7, 6, "throw LLMException(retryable)", "return")
    seq.msg(6, 3, "propagate", "return")
    seq.msg(3, 2, "propagate", "return")
    seq.msg(2, 1, "updateUI(ERROR, userMessage)", "return")
    seq.msg(1, 0, 'dialog: "Retry" / "Cancel"', "return")
    seq.note(1, "The automatic retries happen inside the Decorator chain.\n"
                "The user is only asked once they are exhausted.")

    seq.render("sd01-perform-code-analysis.svg")


# --------------------------------------------------------------------------- SD02


def sd02():
    seq = Sequence(
        "SD02 - Manage Review Finding (F07) - reject, remember, then undo (F12)",
        ["Developer", "CodeReviewGUI", "AgentController", "CommandHistory",
         "RejectCommand", "ReviewItem", "AgentMemory", "ReviewReport"],
        width=1420, top=130)

    seq.msg(0, 1, 'click "Reject", enters reason')
    seq.msg(1, 2, "rejectFinding(issueId, reason)")
    seq.msg(2, 4, "new RejectCommand(item, report, reason)")
    seq.msg(2, 3, "execute(command)")
    seq.msg(3, 4, "execute()")
    seq.msg(4, 5, "createMemento()")
    seq.msg(5, 4, "Memento (status + feedback)", "return")
    seq.note(4, "The command is the Memento caretaker: it holds the\n"
                "snapshot but cannot read inside it.")
    seq.msg(4, 5, "updateStatus(REJECTED, reason)")
    seq.msg(4, 7, "itemChanged(item)")
    seq.msg(7, 1, "updateUI(ITEM_CHANGED, item)", "return")
    seq.msg(3, 2, "pushed onto the undo stack", "return")
    seq.msg(2, 6, "rememberDecision(item)")
    seq.self_msg(6, "persist to .agent-memory/memory.json\nso future prompts carry this decision")
    seq.msg(1, 0, "row greys out; Undo button enables", "return")

    seq.divider("Developer changes their mind")

    seq.msg(0, 1, 'click "Undo"')
    seq.msg(1, 2, "undo()")
    seq.msg(2, 3, "undo()")
    seq.msg(3, 4, "undo()")
    seq.msg(4, 5, "restore(memento)")
    seq.msg(4, 7, "itemChanged(item)")
    seq.msg(7, 1, "updateUI(ITEM_CHANGED, item)", "return")
    seq.msg(2, 1, "description of the undone action", "return")
    seq.msg(1, 0, "finding returns to Pending", "return")

    seq.render("sd02-manage-review-finding.svg")


# --------------------------------------------------------------------------- SD03


def sd03():
    seq = Sequence(
        "SD03 - Generate and Export Report (F09)",
        ["Developer", "CodeReviewGUI", "AgentController", "ExporterFactory",
         "PdfExporter", "ReviewReport", "SimplePdfWriter"],
        width=1340, top=130)

    seq.msg(0, 1, 'click "Export report", chooses PDF')
    seq.msg(1, 2, "exportReport(path, \"pdf\")")
    seq.msg(2, 3, "create(\"pdf\")")
    seq.msg(3, 2, "PdfExporter", "return")
    seq.note(3, "Factory Method: neither view names\na concrete exporter class.")
    seq.msg(2, 4, "setActionLog(...) / setUsageSummary(...) / setAgentTrace(...)")
    seq.msg(2, 5, "aggregateItems()")
    seq.msg(5, 2, "counts by status and severity", "return")
    seq.msg(2, 4, "export(report, path)")

    seq.frame_start("Template Method: fixed section order")
    seq.msg(4, 6, "writeHeader -> addTitle(), addMeta()")
    seq.msg(4, 6, "writeSummary -> addKeyValueTable()")
    seq.msg(4, 5, "getSorted() + groupByFile()")
    seq.msg(5, 4, "findings, severity-ordered", "return")
    seq.msg(4, 6, "writeFindings -> addBadgedHeadline() per finding")
    seq.msg(4, 6, "writeDecisionRecord -> addBullet() per decision")
    seq.msg(4, 6, "writeFooter -> addRule(), addMeta()")
    seq.frame_end()

    seq.msg(4, 6, "writeToDisk hook -> build() + write(path)")
    seq.self_msg(6, "assemble PDF objects, xref table\nand trailer; no external library")
    seq.msg(4, 2, "file written", "return")
    seq.msg(2, 5, "publish(EXPORT_COMPLETE, path)")
    seq.msg(5, 1, "updateUI(EXPORT_COMPLETE, path)", "return")
    seq.msg(1, 0, "confirmation dialog with the file path", "return")

    seq.render("sd03-generate-export-report.svg")


# --------------------------------------------------------------------------- SD04


def sd04():
    seq = Sequence(
        "SD04 (NEW) - Autonomous Multi-Step Audit (F11) - the agent reasoning loop",
        ["Developer", "CodeReviewGUI", "AgentController", "AgentPlanner",
         "LLMClient", "ToolRegistry", "AnalyzeFileTool", "AgentMemory"],
        width=1460, top=130)

    seq.msg(0, 1, 'click "Run autonomous audit", states a goal')
    seq.msg(1, 2, "runAudit(goal, maxSteps, listener)")
    seq.msg(2, 3, "run(goal, maxSteps)")

    seq.frame_start("1. PLAN")
    seq.msg(3, 5, "execute(\"list_files\", \"\")")
    seq.msg(5, 3, "repository inventory", "return")
    seq.msg(3, 4, "sendPrompt(planner role, goal + tools + inventory)")
    seq.msg(4, 3, "ordered plan, grounded in real files", "return")
    seq.msg(3, 1, "updateUI(AGENT_STEP, PLAN)", "return")
    seq.frame_end()

    seq.frame_start("loop  [until the goal is met or the step budget is spent]")
    seq.msg(3, 7, "renderShortTerm(12)")
    seq.msg(7, 3, "everything observed so far", "return")
    seq.msg(3, 4, "sendConversation(goal + plan + observations)")
    seq.msg(4, 3, '{"thought":..., "tool":..., "input":...}', "return")
    seq.note(3, "2. DECIDE - exactly one action per turn.\n"
                "A repeated action is blocked and fed back\n"
                "as an observation the agent must react to.")
    seq.msg(3, 5, "execute(toolName, toolInput)")
    seq.msg(5, 6, "execute(\"LoginService.java|security\")")
    seq.msg(6, 5, "findings recorded in the report", "return")
    seq.msg(5, 3, "observation text", "return")
    seq.msg(3, 7, "remember(observation)")
    seq.msg(3, 1, "updateUI(AGENT_STEP, ACTION / OBSERVATION)", "return")
    seq.frame_end()

    seq.frame_start("5. CONCLUDE")
    seq.msg(3, 4, "sendPrompt(reporter role, all observations)")
    seq.msg(4, 3, "written synthesis", "return")
    seq.msg(3, 2, "conclusion", "return")
    seq.msg(2, 1, "updateUI(TASK_FINISHED)", "return")
    seq.msg(1, 0, "trace panel + findings table are populated", "return")
    seq.frame_end()

    seq.render("sd04-autonomous-audit.svg")


if __name__ == "__main__":
    use_case_diagram()
    sd01()
    sd02()
    sd03()
    sd04()
    print("done")
