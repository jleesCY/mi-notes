"use client";

import { Footer } from "@/components/footer";
import { Navbar } from "@/components/navbar";
import { useHighlight } from "@/hooks/useHighlight";
import { motion } from "framer-motion";
import {
  ArrowUp,
  Bold,
  BookOpen,
  CheckSquare,
  ChevronRight,
  Code2,
  Copy,
  Download,
  FileText,
  FolderOpen,
  FolderPlus,
  Hand,
  Highlighter,
  Indent,
  Info,
  Layout,
  List,
  ListOrdered,
  Lock,
  Minus,
  MousePointer2,
  Navigation,
  Palette,
  Pencil,
  Pin,
  Plus,
  Quote,
  Redo2,
  RefreshCw,
  RotateCcw,
  Search,
  Settings2,
  Share2,
  ShieldCheck,
  StickyNote,
  Trash2,
  Type,
  Undo2,
  Upload,
} from "lucide-react";

const getAssetPath = (path: string) => {
  const isProd = process.env.NODE_ENV === 'production';
  const repoName = process.env.NEXT_PUBLIC_REPO_NAME || "mi-notes";
  const basePath = isProd ? `/${repoName}` : "";
  return `${basePath}${path}`;
};

export default function UserDocs() {
  useHighlight();
  const fadeInUp = {
    initial: { opacity: 0, y: 20 },
    whileInView: { opacity: 1, y: 0 },
    viewport: { once: true },
    transition: { duration: 0.6 }
  };

  return (
    <main className="min-h-screen bg-surface-container-low text-on-surface transition-colors duration-300 overflow-x-hidden">
      <Navbar />

      <div className="pt-24 sm:pt-32 pb-20 px-6">
        <div className="max-w-4xl mx-auto">
          <motion.div {...fadeInUp} className="mb-16 text-center sm:text-left">
            <h1 className="text-4xl sm:text-6xl font-extrabold tracking-tighter mb-6">
              The Complete <span className="text-primary italic">User Guide</span>
            </h1>
            <p className="text-lg sm:text-xl text-on-surface-variant leading-relaxed font-medium">
              Every gesture, tool and setting in mi. Notes.
            </p>
          </motion.div>

          <section className="space-y-24 sm:space-y-32">

            {/* 1. The Pill - IMAGE RIGHT */}
            <motion.div id="the-pill" {...fadeInUp} className="space-y-12">
              <div className="flex items-center justify-center sm:justify-start gap-3">
                <div className="p-2 rounded-xl bg-primary-container text-on-primary-container">
                  <Navigation size={24} />
                </div>
                <h2 className="text-2xl sm:text-3xl font-bold tracking-tight">The Pill</h2>
              </div>
              <div className="grid lg:grid-cols-2 gap-12 items-center text-center sm:text-left">
                <div className="space-y-6">
                  <p className="text-base sm:text-lg text-on-surface-variant leading-relaxed font-medium">
                    mi. Notes is made for one hand. Your notes fill the page, and everything else is in the Pill at the bottom of the screen.
                  </p>
                  <div className="space-y-6 inline-block text-left w-full">
                    <div className="space-y-4">
                      <GuideItem icon={<Plus size={18} />} title="New Note" desc="Tap + in the middle of the Pill. The note opens with the keyboard up, in the folder you're looking at." />
                      <GuideItem icon={<Search size={18} />} title="Search" desc="Tap the magnifier, or hold the Pill: it widens into a search field and your notes narrow as you type." />
                      <GuideItem icon={<MousePointer2 size={18} />} title="The ⋮ Menu" desc="New Checklist, New Folder, and the view: Layout (Staggered, Grid or List) and Sort By (Manual, Edited, Created or Title)." />
                      <GuideItem icon={<ChevronRight size={18} />} title="Swipe Right" desc="Recently Deleted comes in from the left with your finger. Let go past the tick to open it." />
                      <GuideItem icon={<ChevronRight size={18} className="rotate-180" />} title="Swipe Left" desc="Settings comes in from the right. Inside a section, swipe its Pill right to go up a level." />
                    </div>

                    <div id="gestures" className="pt-4 space-y-4 border-t border-outline-variant">
                      <h4 className="text-xs font-bold uppercase tracking-[0.2em] text-primary mb-4">In a Note</h4>
                      <GuideItem icon={<Undo2 size={18} />} title="Swipe Right" desc="Undo. Keep swiping to undo further." />
                      <GuideItem icon={<Redo2 size={18} />} title="Swipe Left" desc="Redo what you undid." />
                      <GuideItem icon={<ArrowUp size={18} />} title="Swipe Up" desc="Back to your notes. The note is already saved." />
                    </div>

                    <div className="p-4 rounded-xl border border-outline-variant bg-surface-container-high text-[10px] sm:text-xs text-on-surface-variant">
                      <p><strong>Pro Tip:</strong> Settings › About › Gesture Walkthrough lets you try every swipe again, step by step.</p>
                    </div>
                  </div>
                </div>
                <DeviceMockup src="/images/home.jpg" color="blue" />
              </div>
            </motion.div>

            {/* 2. Writing - IMAGE LEFT */}
            <motion.div id="writing" {...fadeInUp} className="space-y-12">
              <div className="flex items-center justify-center sm:justify-start gap-3">
                <div className="p-2 rounded-xl bg-secondary-container text-on-secondary-container">
                  <Pencil size={24} />
                </div>
                <h2 className="text-2xl sm:text-3xl font-bold tracking-tight">Writing</h2>
              </div>
              <div className="grid lg:grid-cols-2 gap-12 items-center text-center sm:text-left lg:flex-row-reverse">
                <div className="order-2 lg:order-1">
                  <DeviceMockup src="/images/keyboard.jpg" color="orange" />
                </div>
                <div className="space-y-6 order-1 lg:order-2">
                  <p className="text-base sm:text-lg text-on-surface-variant leading-relaxed font-medium">
                    A note is a title and its text. In a note the Pill becomes your toolbar, and while you type it docks above the keyboard. Everything is saved as you go: there&apos;s no Save button.
                  </p>
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-left">
                    <GuideItem icon={<Type size={18} />} title="Aa" desc="The formatting panel: styles, sizes, colours and more." />
                    <GuideItem icon={<Bold size={18} />} title="Bold" desc="Bold the selection, or what you type next." />
                    <GuideItem icon={<CheckSquare size={18} />} title="Checklist" desc="Turn the line into a checklist item." />
                    <GuideItem icon={<List size={18} />} title="Bullets" desc="Turn the line into a bulleted list." />
                    <GuideItem icon={<ListOrdered size={18} />} title="Numbers" desc="Turn the line into a numbered list." />
                    <GuideItem icon={<Plus size={18} />} title="Insert" desc="Add a divider, quote, code block or callout." />
                  </div>
                  <div className="p-4 rounded-xl border border-outline-variant bg-surface-container-high text-[10px] sm:text-xs text-on-surface-variant">
                    <p><strong>At the Top:</strong> the note&apos;s folder, when it was edited and how many words it has. The pin pins it; ⋮ opens the same menu as holding the note on the home page.</p>
                  </div>
                </div>
              </div>
            </motion.div>

            {/* 3. Formatting - IMAGE RIGHT */}
            <motion.div id="formatting" {...fadeInUp} className="space-y-12">
              <div className="flex items-center justify-center sm:justify-start gap-3">
                <div className="p-2 rounded-xl bg-primary-container text-on-primary-container">
                  <Type size={24} />
                </div>
                <h2 className="text-2xl sm:text-3xl font-bold tracking-tight">Formatting</h2>
              </div>
              <div className="grid lg:grid-cols-2 gap-12 items-center text-center sm:text-left">
                <div className="space-y-6">
                  <p className="text-base sm:text-lg text-on-surface-variant leading-relaxed font-medium">
                    Tap Aa and the panel slides up where the keyboard was. Select text first to change it, or set a style and keep typing.
                  </p>
                  <div className="space-y-4 inline-block text-left w-full">
                    <GuideItem icon={<Type size={18} />} title="Styles" desc="Title, Heading, Subheading and Body set the size and weight of the whole paragraph." />
                    <GuideItem icon={<Bold size={18} />} title="Bold, Italic, Underline, Strikethrough" desc="One tap each. Clear Formatting takes every inline style off the selection." />
                    <GuideItem icon={<Plus size={18} />} title="Text Size" desc="Step it with − and +, or tap the number and type any size you like." />
                    <GuideItem icon={<Highlighter size={18} />} title="Text Color and Highlight" desc="Pick a colour for the letters or for behind them, or None to take it off." />
                    <GuideItem icon={<Indent size={18} />} title="Alignment and Indent" desc="Left, centre or right; indent a list item in or out, up to six levels." />
                  </div>
                  <div className="p-4 rounded-xl border border-outline-variant bg-surface-container-high text-[10px] sm:text-xs text-on-surface-variant">
                    <p><strong>Pro Tip:</strong> your selection stays while the panel is open, so you can make it bold, bigger and yellow in one go.</p>
                  </div>
                </div>
                <DeviceMockup src="/images/format.jpg" color="purple" />
              </div>
            </motion.div>

            {/* 4. Lists & Blocks - IMAGE LEFT */}
            <motion.div id="lists" {...fadeInUp} className="space-y-12">
              <div className="flex items-center justify-center sm:justify-start gap-3">
                <div className="p-2 rounded-xl bg-tertiary-container text-on-tertiary-container">
                  <ListOrdered size={24} />
                </div>
                <h2 className="text-2xl sm:text-3xl font-bold tracking-tight">Lists &amp; Blocks</h2>
              </div>
              <div className="grid lg:grid-cols-2 gap-12 items-center text-center sm:text-left lg:flex-row-reverse">
                <div className="order-2 lg:order-1">
                  <DeviceMockup src="/images/insert.jpg" color="cyan" />
                </div>
                <div className="space-y-6 order-1 lg:order-2">
                  <p className="text-base sm:text-lg text-on-surface-variant leading-relaxed font-medium">
                    Lists and blocks behave the way they do in every other editor.
                  </p>
                  <div className="space-y-4 inline-block text-left">
                    <GuideItem icon={<List size={18} />} title="Enter Carries On" desc="Enter starts the next item of a list, or the next line of a quote, code block or callout." />
                    <GuideItem icon={<CornerOut />} title="Enter Twice Ends It" desc="Press Enter on an empty item and the list (or block) ends there; you're back to plain text." />
                    <GuideItem icon={<Undo2 size={18} />} title="Backspace at the Start" desc="Takes the bullet, number, checkbox or block off the line, keeping its text." />
                    <div id="checklists">
                      <GuideItem icon={<CheckSquare size={18} />} title="Checklists" desc="Tap a checkbox to tick it off. With Checked Items to Bottom on (Settings › Notes), ticked items move below the rest." />
                    </div>
                    <div id="blocks">
                      <GuideItem icon={<Minus size={18} />} title="Divider" desc="A line across the page, between two parts of a note." />
                      <GuideItem icon={<Quote size={18} />} title="Quote" desc="Sets a paragraph apart with a bar at its side." />
                      <GuideItem icon={<Code2 size={18} />} title="Code" desc="Monospaced, on a tinted block, with no autocorrect or capital letters getting in the way." />
                      <GuideItem icon={<Info size={18} />} title="Callout" desc="A tinted note with an accent bar, for the line you mustn't miss." />
                    </div>
                  </div>
                </div>
              </div>
            </motion.div>

            {/* 5. Organizing - IMAGE RIGHT */}
            <motion.div id="organizing" {...fadeInUp} className="space-y-12">
              <div className="flex items-center justify-center sm:justify-start gap-3">
                <div className="p-2 rounded-xl bg-tertiary-container text-on-tertiary-container">
                  <FolderOpen size={24} />
                </div>
                <h2 className="text-2xl sm:text-3xl font-bold tracking-tight">Organizing Notes</h2>
              </div>
              <div className="grid lg:grid-cols-2 gap-12 items-center text-center sm:text-left">
                <div className="space-y-6">
                  <p className="text-base sm:text-lg text-on-surface-variant leading-relaxed font-medium">
                    Folders sit as chips across the top of the home page. Pinned notes always come first.
                  </p>
                  <div className="space-y-4 inline-block text-left w-full">
                    <div id="folders">
                      <GuideItem icon={<FolderPlus size={18} />} title="Folders" desc="Tap a chip to see that folder, or All Notes for everything. Make one from + at the end of the chips or the ⋮ menu; hold a chip to rename or delete it (its notes move to No Folder, and Undo puts it all back)." />
                    </div>
                    <div id="hold">
                      <GuideItem icon={<Hand size={18} />} title="Hold a Note" desc="Hold a note to pick it up, then drag it somewhere else (Sort By Manual keeps your order). Let go without moving for its menu." />
                    </div>
                    <div id="note-menu">
                      <GuideItem icon={<Pin size={18} />} title="Pin, Share, Move, Rename" desc="The big buttons at the top of a note's menu. Share sends it as plain text; Move puts it in another folder." />
                      <GuideItem icon={<Palette size={18} />} title="Colour" desc="Give the note's card one of nine colours, or none." />
                      <GuideItem icon={<FileText size={18} />} title="Tools" desc="Markdown shares the note as a .md file; Export saves it as one; Duplicate makes a copy; Info shows when it was made and edited and how long it is. The copy button copies its text." />
                    </div>
                    <div id="swipe-rows">
                      <GuideItem icon={<Layout size={18} />} title="Swipe Rows" desc="In the List layout, swipe a note left to delete it or right to pin it (Settings › Gestures)." />
                    </div>
                    <GuideItem icon={<RotateCcw size={18} />} title="Undo" desc="Every delete shows Undo for a few seconds, and the note waits in Recently Deleted after that." />
                  </div>
                </div>
                <DeviceMockup src="/images/menu.jpg" color="pink" />
              </div>
            </motion.div>

            {/* 6. Search - IMAGE LEFT */}
            <motion.div id="search" {...fadeInUp} className="space-y-12">
              <div className="flex items-center justify-center sm:justify-start gap-3">
                <div className="p-2 rounded-xl bg-secondary-container text-on-secondary-container">
                  <Search size={24} />
                </div>
                <h2 className="text-2xl sm:text-3xl font-bold tracking-tight">Search</h2>
              </div>
              <div className="grid lg:grid-cols-2 gap-12 items-center text-center sm:text-left lg:flex-row-reverse">
                <div className="order-2 lg:order-1">
                  <DeviceMockup src="/images/search.jpg" color="green" />
                </div>
                <div className="space-y-6 order-1 lg:order-2">
                  <p className="text-base sm:text-lg text-on-surface-variant leading-relaxed font-medium">
                    Hold the Pill (or tap the magnifier) and start typing. Titles and text are both searched, in the folder you&apos;re looking at, and the list narrows with every letter.
                  </p>
                  <div className="space-y-4 inline-block text-left">
                    <GuideItem icon={<StickyNote size={18} />} title="Open a Result" desc="Tap a note to open it. Come back and your search is still there, so you can open the next one." />
                    <GuideItem icon={<Search size={18} />} title="No Matches" desc="The field gives a little shake when nothing matches." />
                    <GuideItem icon={<Settings2 size={18} />} title="Long Press to Search" desc="Settings › Gestures turns holding the Pill on or off. The magnifier always works." />
                  </div>
                </div>
              </div>
            </motion.div>

            {/* 7. Recently Deleted - IMAGE RIGHT */}
            <motion.div id="recently-deleted" {...fadeInUp} className="space-y-12">
              <div className="flex items-center justify-center sm:justify-start gap-3">
                <div className="p-2 rounded-xl bg-primary-container text-on-primary-container">
                  <Trash2 size={24} />
                </div>
                <h2 className="text-2xl sm:text-3xl font-bold tracking-tight">Recently Deleted</h2>
              </div>
              <div className="grid lg:grid-cols-2 gap-12 items-center text-center sm:text-left">
                <div className="space-y-6">
                  <p className="text-base sm:text-lg text-on-surface-variant leading-relaxed font-medium">
                    Swipe the Pill right. Notes you delete wait here for 30 days (or a week, or 90 days: Settings › Recently Deleted), then they&apos;re gone for good.
                  </p>
                  <div className="space-y-4 inline-block text-left w-full">
                    <GuideItem icon={<RotateCcw size={18} />} title="Restore" desc="Tap a note, or its restore button, to put it back where it was." />
                    <GuideItem icon={<Trash2 size={18} />} title="Delete Forever" desc="Swipe a note left. You're asked first: this one can't be undone." />
                    <GuideItem icon={<Search size={18} />} title="Search" desc="The Pill here searches your deleted notes; its ⋮ empties Recently Deleted." />
                  </div>
                </div>
                <DeviceMockup src="/images/trash.jpg" color="orange" />
              </div>
            </motion.div>

            {/* 8. Detailed Settings Reference - IMAGE LEFT */}
            <motion.div id="customization" {...fadeInUp} className="space-y-12">
              <div className="flex items-center justify-center sm:justify-start gap-3">
                <div className="p-2 rounded-xl bg-secondary-container text-on-secondary-container">
                  <Settings2 size={24} />
                </div>
                <h2 className="text-2xl sm:text-3xl font-bold tracking-tight">Settings</h2>
              </div>

              <div className="grid lg:grid-cols-2 gap-12 items-center text-center sm:text-left">
                <div className="space-y-6">
                  <p className="text-lg text-on-surface-variant leading-relaxed font-medium">
                    Swipe the Pill left for Settings. It opens on a list of sections; tap one to open its page, or search to find any setting from any section. Here is every setting:
                  </p>
                  <div className="p-4 rounded-xl border border-outline-variant bg-surface-container-high text-[10px] sm:text-xs text-on-surface-variant">
                    <p><strong>Pro Tip:</strong> Settings apply as you change them. Pick an accent colour and the whole app recolours behind the page.</p>
                  </div>
                </div>
                <DeviceMockup src="/images/look.jpg" color="orange" />
              </div>

              <div className="grid gap-12">
                <SettingsSection title="Look & Feel" icon={<Palette size={20} />}>
                  <SettingItem title="Theme" desc="Light, Dark, or follow the System." />
                  <SettingItem title="Pure Black" desc="True black backgrounds in dark mode, for OLED screens." />
                  <SettingItem title="Accent Color" desc="Your wallpaper's colours, or one of the swatches. Yellow is mi. Notes' own." />
                  <SettingItem title="Font Size" desc="The size of the app's own text, 80% to 120%." />
                  <SettingItem title="Font" desc="Standard (Google Sans Flex), Rounded, or your phone's System font." />
                  <SettingItem title="Pan Overflowed Text" desc="Names too long to fit slide sideways to show the rest. Choose where: Settings, the Note Menu, Notes and Recently Deleted." />
                  <SettingItem title="Corners" desc="Square, Soft or Round corners on cards, rows and buttons." />
                  <SettingItem title="List Spacing" desc="Compact, Normal or Airy space between items." />
                  <SettingItem title="List Style" desc="How the note list and Recently Deleted look: Cards or Table." />
                  <SettingItem title="Status Bar" desc="Show the clock and battery, or hide them for a cleaner page." />
                  <SettingItem title="Rotate Screen" desc="Turn mi. Notes sideways with your phone." />
                  <SettingItem title="Gesture Haptics" desc="Haptic feedback when gestures are performed." />
                </SettingsSection>

                <SettingsSection title="Notes" icon={<StickyNote size={20} />}>
                  <SettingItem title="Layout" desc="Staggered cards fit their note; Grid cards are all one size; List shows one row per note." />
                  <SettingItem title="Sort By" desc="Manual (your order), Edited, Created or Title. Pinned notes always come first." />
                  <SettingItem title="Preview Lines" desc="How much of each note its card shows: 3, 6, 9 or 12 lines." />
                  <SettingItem title="Show Dates" desc="When each note was last edited, on its card." />
                  <SettingItem title="Text Size" desc="The size of the text in your notes, 80% to 160%." />
                  <SettingItem title="Checked Items to Bottom" desc="Ticked items move below the rest of their checklist." />
                </SettingsSection>

                <SettingsSection title="Gestures" icon={<Hand size={20} />}>
                  <SettingItem title="Long Press to Search" desc="Hold the home Pill to search your notes." />
                  <SettingItem title="Swipe Rows" desc="In the List layout, swipe a note left to delete it or right to pin it." />
                </SettingsSection>

                <SettingsSection title="Recently Deleted" icon={<Trash2 size={20} />} id="recently-deleted-settings">
                  <SettingItem title="Keep Deleted Notes" desc="How long deleted notes wait: 1 Week, 30 Days or 90 Days." />
                  <SettingItem title="Empty Recently Deleted" desc="Delete every note in Recently Deleted for good." />
                </SettingsSection>

                <SettingsSection title="Backup & Reset" icon={<RefreshCw size={20} />}>
                  <SettingItem title="Export Settings" desc="Save your settings and notes to one .minotesdata file." />
                  <SettingItem title="Import Settings" desc="Restore from an exported file: your settings are replaced and its notes are added beside yours." />
                  <SettingItem title="Export as Markdown" desc="Every note as a .md file, in a zip, for any notes app." />
                  <SettingItem title="Import Notes" desc="Markdown or text files become notes in the folder showing." />
                  <SettingItem title="Reset All Settings" desc="Every setting back to its default. Your notes and folders stay." />
                  <SettingItem title="Wipe All Data" desc="Delete every note, folder and setting, and start again as if just installed." />
                </SettingsSection>

                <SettingsSection title="About" icon={<BookOpen size={20} />}>
                  <SettingItem title="Check for Updates" desc="Ask GitHub for a newer version now. mi. Notes also checks by itself, at most every 12 hours." />
                  <SettingItem title="Gesture Walkthrough" desc="Try every swipe again, step by step." />
                  <SettingItem title="User Guide" desc="This page." />
                  <SettingItem title="Report a Bug or Suggest a Feature" desc="Open a new issue on GitHub." />
                  <SettingItem title="Privacy Policy" desc="mi. Notes collects no personal data." />
                  <SettingItem title="Source Code" desc="The app's code, on GitHub." />
                  <SettingItem title="Credits" desc="The open-source projects mi. Notes is built with." />
                </SettingsSection>
              </div>
            </motion.div>

            {/* 9. Your Data */}
            <motion.div id="your-data" {...fadeInUp} className="space-y-12">
              <div className="flex items-center justify-center sm:justify-start gap-3">
                <div className="p-2 rounded-xl bg-tertiary-container text-on-tertiary-container">
                  <Download size={24} />
                </div>
                <h2 className="text-2xl sm:text-3xl font-bold tracking-tight">Your Data</h2>
              </div>
              <div className="space-y-6">
                <p className="text-base sm:text-lg text-on-surface-variant leading-relaxed font-medium text-center sm:text-left">
                  Your notes live in the app&apos;s private storage on your phone, in a plain format. These are the ways in and out.
                </p>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-6 text-left">
                  <div id="backups">
                    <GuideItem icon={<Upload size={18} />} title="Backups" desc="Settings › Backup & Reset › Export Settings saves everything to one .minotesdata file. Import Settings on a new phone brings it all back; notes you already have stay." />
                  </div>
                  <div id="markdown">
                    <GuideItem icon={<FileText size={18} />} title="Markdown" desc="Export as Markdown gives you every note as a .md file in a zip. Lists, checklists, bold, italics, dividers, quotes, code and callouts (as GitHub's > [!NOTE]) come out as Markdown, and come back the same way with Import Notes." />
                  </div>
                  <GuideItem icon={<Share2 size={18} />} title="Share a Note" desc="Share sends a note as plain text; Markdown sends it as a .md file. Export saves that file wherever you choose." />
                  <div id="share-to">
                    <GuideItem icon={<StickyNote size={18} />} title="Send Text to mi. Notes" desc="In any app, share some text and choose mi. Notes: it becomes a new note." />
                  </div>
                  <GuideItem icon={<Copy size={18} />} title="Copy" desc="The copy button in a note's menu copies its text." />
                  <div id="updates">
                    <GuideItem icon={<RefreshCw size={18} />} title="Updates" desc="mi. Notes checks GitHub for a new version and offers to install it. After an update it shows What's New." />
                  </div>
                </div>
              </div>
            </motion.div>

            {/* 10. Privacy Commitment */}
            <motion.div id="privacy" {...fadeInUp} className="bg-primary-container rounded-[2rem] p-12 text-on-primary-container relative overflow-hidden">
              <div className="absolute top-0 right-0 p-12 opacity-10">
                <ShieldCheck size={200} />
              </div>
              <div className="relative z-10 max-w-2xl">
                <h2 className="text-3xl font-extrabold mb-6 tracking-tight">Privacy by Design</h2>
                <p className="text-xl text-on-primary-container font-medium leading-relaxed mb-8">
                  mi. Notes is minimal in design and in data collection. Your notes remain yours.
                </p>
                <div className="grid sm:grid-cols-2 gap-8">
                  <div className="space-y-2">
                    <div className="flex items-center gap-2 font-bold">
                      <Lock size={18} /> No Tracking
                    </div>
                    <p className="text-sm text-on-primary-container/80">No accounts, no analytics, no ads, no crash reporters. The only connection is to GitHub, to check for a newer version.</p>
                  </div>
                  <div className="space-y-2">
                    <div className="flex items-center gap-2 font-bold">
                      <RefreshCw size={18} /> Local Storage
                    </div>
                    <p className="text-sm text-on-primary-container/80">Notes, folders and settings are stored only in the app&apos;s private storage on your phone. They leave it only when you share, copy or export them.</p>
                  </div>
                </div>
              </div>
            </motion.div>
          </section>
        </div>
      </div>

      <Footer />
    </main>
  );
}

/** Enter twice: a list's marker going away (lucide has no "end list" icon). */
function CornerOut() {
  return <span className="font-black text-sm leading-none">↵↵</span>;
}

function GuideItem({ icon, title, desc }: { icon: React.ReactNode, title: string, desc: string }) {
  return (
    <div className="flex gap-4">
      <div className="shrink-0 w-10 h-10 rounded-xl bg-surface-container-high border border-outline-variant flex items-center justify-center text-on-surface-variant/80">
        {icon}
      </div>
      <div>
        <h4 className="font-bold text-lg leading-tight mb-1">{title}</h4>
        <p className="text-on-surface-variant font-medium leading-relaxed">{desc}</p>
      </div>
    </div>
  );
}

function SettingsSection({ title, icon, children, id: given }: { title: string, icon: React.ReactNode, children: React.ReactNode, id?: string }) {
  const id = given ?? title.toLowerCase().replace(/&/g, 'and').replace(/\s+/g, '-');
  return (
    <div id={id} className="space-y-6 scroll-mt-28">
      <div className="flex items-center gap-2 pb-2 border-b border-outline-variant">
        <div className="text-primary">{icon}</div>
        <h3 className="font-bold text-xl tracking-tight uppercase text-xs tracking-[0.2em] opacity-50">{title}</h3>
      </div>
      <div className="grid sm:grid-cols-2 gap-6">
        {children}
      </div>
    </div>
  );
}

function SettingItem({ title, desc, id: given }: { title: string, desc: string, id?: string }) {
  // its link (#setting-…) from its name
  const id = given ?? `setting-${title.toLowerCase().replace(/&/g, 'and').replace(/\s+/g, '-')}`;
  return (
    <div id={id} className="p-6 rounded-2xl bg-surface-container-high border border-outline-variant shadow-sm hover:border-primary/40 transition-colors scroll-mt-28">
      <h4 className="font-bold text-sm mb-2">{title}</h4>
      <p className="text-xs text-on-surface-variant leading-relaxed font-medium">{desc}</p>
    </div>
  );
}

function DeviceMockup({ src, color }: { src: string, color?: string }) {
  const glowStyles: any = {
    blue: "bg-primary/20 shadow-primary/30",
    purple: "bg-tertiary/20 shadow-tertiary/30",
    pink: "bg-tertiary/20 shadow-tertiary/30",
    orange: "bg-secondary/20 shadow-secondary/30",
    cyan: "bg-secondary/20 shadow-secondary/30",
    green: "bg-secondary/20 shadow-secondary/30",
  };

  const glowClass = (color && glowStyles[color]) ? glowStyles[color].split(' ')[0] : 'bg-primary-container';

  return (
    <div className="relative group flex justify-center">
      <div className={`absolute -inset-10 ${glowClass} rounded-full blur-[80px] group-hover:opacity-100 transition-opacity opacity-50`} />
      <div className={`relative w-[220px] sm:w-[280px] bg-black rounded-[2rem] p-2 shadow-2xl border-[4px] border-[#1a1a1a] overflow-hidden transition-transform duration-500 group-hover:scale-105 group-hover:-rotate-1`}>
        <img
          src={getAssetPath(src)}
          alt="mi. App Screenshot"
          className="w-full h-auto block rounded-[1.4rem]"
        />
      </div>
    </div>
  );
}
