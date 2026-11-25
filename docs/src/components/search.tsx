"use client";

import { Code2, FileText, Search as SearchIcon, Settings2, Shield, User, X } from "lucide-react";
import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";

interface SearchResult {
  title: string;
  type: "User Guide" | "Dev Guide" | "Settings" | "Architecture";
  href: string;
  excerpt: string;
}

const MOCK_DATA: SearchResult[] = [
  // User Guide Sections
  { title: "The Pill", type: "User Guide", href: "/user#the-pill", excerpt: "New note, search, the ⋮ menu, and swipes for Recently Deleted and Settings." },
  { title: "New Note", type: "User Guide", href: "/user#the-pill", excerpt: "Tap + in the middle of the Pill: the note opens with the keyboard up." },
  { title: "Gestures in a Note", type: "User Guide", href: "/user#gestures", excerpt: "Swipe the Pill right to undo, left to redo, up to go back to your notes." },
  { title: "Undo and Redo", type: "User Guide", href: "/user#gestures", excerpt: "Swipe the note's Pill right to undo and left to redo." },
  { title: "Writing", type: "User Guide", href: "/user#writing", excerpt: "The toolbar Pill: Aa, bold, checklist, bullets, numbers and insert. Saves as you type." },
  { title: "Formatting", type: "User Guide", href: "/user#formatting", excerpt: "Title, Heading, Subheading; bold, italic, underline; sizes, colours, highlights, alignment." },
  { title: "Text Color and Highlight", type: "User Guide", href: "/user#formatting", excerpt: "Pick a colour for the letters or for behind them." },
  { title: "Lists & Blocks", type: "User Guide", href: "/user#lists", excerpt: "Enter carries a list on; Enter twice ends it. Backspace at the start takes the marker off." },
  { title: "Checklists", type: "User Guide", href: "/user#checklists", excerpt: "Tap a checkbox to tick it off; ticked items can move to the bottom." },
  { title: "Dividers, Quotes, Code and Callouts", type: "User Guide", href: "/user#blocks", excerpt: "The + in the toolbar adds a divider, quote, code block or callout." },
  { title: "Folders", type: "User Guide", href: "/user#folders", excerpt: "Folder chips across the top; hold a chip to rename or delete it." },
  { title: "Hold a Note", type: "User Guide", href: "/user#hold", excerpt: "Hold to pick it up and drag it; let go without moving for its menu." },
  { title: "Note Menu", type: "User Guide", href: "/user#note-menu", excerpt: "Pin, share, move, rename, colour, Markdown, export, duplicate and info." },
  { title: "Swipe Rows", type: "User Guide", href: "/user#swipe-rows", excerpt: "In the List layout, swipe a note left to delete it or right to pin it." },
  { title: "Search", type: "User Guide", href: "/user#search", excerpt: "Hold the Pill or tap the magnifier; titles and text are searched." },
  { title: "Recently Deleted", type: "User Guide", href: "/user#recently-deleted", excerpt: "Deleted notes wait 30 days. Tap to restore, swipe left to delete forever." },
  { title: "Settings Reference", type: "User Guide", href: "/user#customization", excerpt: "Every setting in mi. Notes, section by section." },
  { title: "Your Data", type: "User Guide", href: "/user#your-data", excerpt: "Backups, Markdown export and import, sharing, and sending text to mi. Notes." },
  { title: "Backups", type: "User Guide", href: "/user#backups", excerpt: "Everything in one .minotesdata file, restored on any phone." },
  { title: "Markdown", type: "User Guide", href: "/user#markdown", excerpt: "Every note as a .md file in a zip, and Markdown or text files back in as notes." },
  { title: "Send Text to mi. Notes", type: "User Guide", href: "/user#share-to", excerpt: "Share text from any app and choose mi. Notes to make it a note." },
  { title: "Updates", type: "User Guide", href: "/user#updates", excerpt: "mi. Notes checks GitHub for a new version and shows What's New." },
  { title: "Privacy Policy", type: "User Guide", href: "/privacy", excerpt: "mi. Notes collects no personal data. What stays on your device and the one connection mi. makes." },
  { title: "Privacy Commitment", type: "User Guide", href: "/user#privacy", excerpt: "No accounts, no tracking; your notes stay on your phone." },

  // Settings (every setting, matching SettingItem IDs)
  // Look & Feel
  { title: "Theme", type: "Settings", href: "/user#setting-theme", excerpt: "Light, Dark, or follow the System." },
  { title: "Pure Black", type: "Settings", href: "/user#setting-pure-black", excerpt: "True black backgrounds in dark mode." },
  { title: "Accent Color", type: "Settings", href: "/user#setting-accent-color", excerpt: "Your wallpaper's colours, or one of the swatches." },
  { title: "Font Size", type: "Settings", href: "/user#setting-font-size", excerpt: "The size of the app's own text." },
  { title: "Font", type: "Settings", href: "/user#setting-font", excerpt: "Standard, Rounded, or your phone's System font." },
  { title: "Pan Overflowed Text", type: "Settings", href: "/user#setting-pan-overflowed-text", excerpt: "Names too long to fit slide sideways to show the rest." },
  { title: "Corners", type: "Settings", href: "/user#setting-corners", excerpt: "Square, Soft or Round corners." },
  { title: "List Spacing", type: "Settings", href: "/user#setting-list-spacing", excerpt: "Compact, Normal or Airy." },
  { title: "List Style", type: "Settings", href: "/user#setting-list-style", excerpt: "Cards or Table, for the note list and Recently Deleted." },
  { title: "Status Bar", type: "Settings", href: "/user#setting-status-bar", excerpt: "Show or hide the clock and battery." },
  { title: "Rotate Screen", type: "Settings", href: "/user#setting-rotate-screen", excerpt: "Turn mi. Notes sideways with your phone." },
  { title: "Gesture Haptics", type: "Settings", href: "/user#setting-gesture-haptics", excerpt: "Haptic feedback when gestures are performed." },

  // Notes
  { title: "Layout", type: "Settings", href: "/user#setting-layout", excerpt: "Staggered, Grid or List." },
  { title: "Sort By", type: "Settings", href: "/user#setting-sort-by", excerpt: "Manual, Edited, Created or Title." },
  { title: "Preview Lines", type: "Settings", href: "/user#setting-preview-lines", excerpt: "How much of each note its card shows." },
  { title: "Show Dates", type: "Settings", href: "/user#setting-show-dates", excerpt: "When each note was last edited." },
  { title: "Text Size", type: "Settings", href: "/user#setting-text-size", excerpt: "The size of the text in your notes." },
  { title: "Checked Items to Bottom", type: "Settings", href: "/user#setting-checked-items-to-bottom", excerpt: "Ticked items move below the rest of their checklist." },

  // Gestures
  { title: "Long Press to Search", type: "Settings", href: "/user#setting-long-press-to-search", excerpt: "Hold the home Pill to search your notes." },
  { title: "Swipe Rows", type: "Settings", href: "/user#setting-swipe-rows", excerpt: "Swipe a row left to delete, right to pin." },

  // Recently Deleted
  { title: "Keep Deleted Notes", type: "Settings", href: "/user#setting-keep-deleted-notes", excerpt: "1 Week, 30 Days or 90 Days." },
  { title: "Empty Recently Deleted", type: "Settings", href: "/user#setting-empty-recently-deleted", excerpt: "Delete every deleted note for good." },

  // Backup & Reset
  { title: "Export Settings", type: "Settings", href: "/user#setting-export-settings", excerpt: "Save your settings and notes to one file." },
  { title: "Import Settings", type: "Settings", href: "/user#setting-import-settings", excerpt: "Restore from an exported file." },
  { title: "Export as Markdown", type: "Settings", href: "/user#setting-export-as-markdown", excerpt: "Every note as a .md file, in a zip." },
  { title: "Import Notes", type: "Settings", href: "/user#setting-import-notes", excerpt: "Markdown or text files become notes." },
  { title: "Reset All Settings", type: "Settings", href: "/user#setting-reset-all-settings", excerpt: "Back to defaults; your notes stay." },
  { title: "Wipe All Data", type: "Settings", href: "/user#setting-wipe-all-data", excerpt: "Delete everything and start again." },

  // About
  { title: "Check for Updates", type: "Settings", href: "/user#setting-check-for-updates", excerpt: "Ask GitHub for a newer version now." },
  { title: "Gesture Walkthrough", type: "Settings", href: "/user#setting-gesture-walkthrough", excerpt: "Try every swipe again, step by step." },
  { title: "Report a Bug or Suggest a Feature", type: "Settings", href: "/user#setting-report-a-bug-or-suggest-a-feature", excerpt: "Open a new issue on GitHub." },
  { title: "Credits", type: "Settings", href: "/user#setting-credits", excerpt: "The open-source projects mi. Notes is built with." },

  // Dev Guide Sections
  { title: "UI Reference", type: "Dev Guide", href: "/developer#ui-reference", excerpt: "Screenshots of the app's core screens." },
  { title: "Tech Stack", type: "Architecture", href: "/developer#tech-stack", excerpt: "Kotlin, Jetpack Compose, Material 3 Expressive, and a span-based rich text editor." },
  { title: "Project Structure", type: "Architecture", href: "/developer#project-structure", excerpt: "One package per feature: notes, editor, home, trash, onboarding, update." },
  { title: "The Note Format", type: "Architecture", href: "/developer#setup", excerpt: "Plain text plus typed spans, changed only by adding." },
  { title: "Development Setup", type: "Dev Guide", href: "/developer#setup", excerpt: "Clone, build and run the project." },
  { title: "Building & Deployment", type: "Dev Guide", href: "/developer#setup", excerpt: "Build debug and release APKs with Gradle." },
];

export function Search() {
  const [isOpen, setIsOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<SearchResult[]>([]);
  const [mounted, setMounted] = useState(false);
  const searchRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    setMounted(true);
  }, []);

  useEffect(() => {
    if (query.trim() === "") {
      setResults([]);
      return;
    }
    const filtered = MOCK_DATA.filter(item =>
      item.title.toLowerCase().includes(query.toLowerCase()) ||
      item.excerpt.toLowerCase().includes(query.toLowerCase())
    );
    setResults(filtered);
  }, [query]);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "k" && (e.metaKey || e.ctrlKey)) {
        e.preventDefault();
        setIsOpen(true);
      }
      if (e.key === "Escape") setIsOpen(false);
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, []);

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (searchRef.current && !searchRef.current.contains(e.target as Node)) {
        setIsOpen(false);
      }
    };
    if (isOpen) {
      document.addEventListener("mousedown", handleClickOutside);
      document.body.style.overflow = 'hidden';
    } else {
      document.body.style.overflow = '';
    }
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
      document.body.style.overflow = '';
    };
  }, [isOpen]);

  const handleResultClick = (e: React.MouseEvent, href: string) => {
    setIsOpen(false);

    // Check if we're navigating to an anchor on the current page
    const [path, hash] = href.split('#');
    const currentPath = window.location.pathname;
    const normalizedPath = currentPath.endsWith('/') ? currentPath.slice(0, -1) : currentPath;
    const targetPath = path.endsWith('/') ? path.slice(0, -1) : path;

    if (hash && (normalizedPath === targetPath || targetPath === "")) {
      e.preventDefault();
      const element = document.getElementById(hash);
      if (element) {
        // Use 'start' to ensure we see the top of the section
        element.scrollIntoView({ behavior: 'smooth', block: 'start' });

        // Add highlight effect
        element.classList.remove('highlight-section');
        // Trigger reflow
        void element.offsetWidth;
        element.classList.add('highlight-section');

        // Update URL without jump
        window.history.pushState(null, '', href);
      }
    }
  };

  const ModalContent = (
    <div className="fixed inset-0 z-[9999] bg-inverse-surface/40 backdrop-blur-md p-4 sm:p-4 flex items-start justify-center overflow-hidden">
      <div
        ref={searchRef}
        className="w-full max-w-2xl bg-surface-container-high rounded-4xl shadow-2xl border border-outline-variant overflow-hidden mt-12 sm:mt-20 flex flex-col max-h-[70vh] sm:max-h-[80vh]"
      >
        <div className="flex items-center p-4 sm:p-6 border-b border-outline-variant shrink-0">
          <SearchIcon className="text-on-surface-variant/80 mr-3 sm:mr-4" size={20} />
          <input
            autoFocus
            type="text"
            placeholder="Search..."
            className="flex-1 bg-transparent border-none outline-none text-lg sm:text-xl text-on-surface font-medium placeholder:text-on-surface-variant/60"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
          <button
            onClick={() => setIsOpen(false)}
            className="p-2 hover:bg-surface-container-high rounded-xl transition-colors ml-2"
          >
            <X size={20} className="text-on-surface-variant/80" />
          </button>
        </div>
        <div className="p-2 overflow-y-auto flex-1">
          {results.length > 0 ? (
            <div className="space-y-1">
              {results.map((result, idx) => (
                <Link
                  key={idx}
                  href={result.href}
                  onClick={(e) => handleResultClick(e, result.href)}
                  className="flex items-start gap-4 p-4 rounded-2xl hover:bg-surface-container-high transition-colors group"
                >
                  <div className="mt-1 p-2 rounded-lg bg-primary-container text-on-primary-container group-hover:bg-primary group-hover:text-on-primary transition-colors">
                    {result.type === "Settings" ? <Settings2 size={18} /> :
                      result.type === "Architecture" ? <Code2 size={18} /> :
                        result.title === "Privacy Commitment" ? <Shield size={18} /> :
                          result.type === "User Guide" ? <User size={18} /> : <FileText size={18} />}
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 mb-1">
                      <span className="font-bold text-on-surface truncate">{result.title}</span>
                      <span className="shrink-0 text-[9px] font-bold uppercase tracking-widest text-on-surface-variant/70 px-1.5 py-0.5 rounded-md border border-outline-variant">
                        {result.type}
                      </span>
                    </div>
                    <p className="text-xs sm:text-sm text-on-surface-variant line-clamp-2">{result.excerpt}</p>
                  </div>
                </Link>
              ))}
            </div>
          ) : query ? (
            <div className="p-12 text-center text-on-surface-variant/60">
              <p className="text-lg font-medium">No results found for &quot;{query}&quot;</p>
            </div>
          ) : (
            <div className="p-12 text-center text-on-surface-variant/60">
              <p className="text-sm font-medium">Try searching for &quot;Gestures&quot; or &quot;Tech Stack&quot;</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );

  return (
    <>
      <button
        onClick={() => setIsOpen(true)}
        className="flex items-center justify-center gap-1 sm:gap-2 px-2 sm:px-4 py-2 rounded-xl bg-surface-container-high border border-outline-variant text-on-surface-variant/80 hover:bg-surface-container-highest transition-all text-xs sm:text-sm font-semibold w-full sm:max-w-sm"
      >
        <SearchIcon size={14} className="sm:size-4" />
        <span className="text-center">Search</span>
        <kbd className="hidden sm:inline-flex h-5 select-none items-center gap-1 rounded border border-outline-variant bg-surface-container-high px-1.5 font-mono text-[10px] font-bold opacity-100">
          <span className="text-xs">⌘</span>K
        </kbd>
      </button>

      {isOpen && mounted && createPortal(ModalContent, document.body)}
    </>
  );
}