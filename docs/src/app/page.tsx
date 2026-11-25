"use client";

import { motion } from "framer-motion";
import { Download, ChevronRight, Zap, Palette, Shield, Sparkles, FolderOpen, Check } from "lucide-react";
import { Navbar } from "@/components/navbar";
import { Footer } from "@/components/footer";

// Helper to handle basePath for GitHub Pages
const getAssetPath = (path: string) => {
  const isProd = process.env.NODE_ENV === 'production';
  const repoName = process.env.NEXT_PUBLIC_REPO_NAME || "mi-notes";
  const basePath = isProd ? `/${repoName}` : "";
  return `${basePath}${path}`;
};

export default function Home() {
  const GITHUB_URL = "https://github.com/jleescy/mi-notes";

  return (
    <main className="min-h-screen bg-surface-container-low text-on-surface overflow-x-hidden transition-colors duration-300">
      <Navbar />

      {/* Hero Section */}
      <section className="pt-32 sm:pt-48 pb-16 sm:pb-20 px-6 relative">
        <div className="absolute top-20 left-1/2 -translate-x-1/2 w-full max-w-[600px] h-[300px] sm:h-[600px] bg-primary/10 rounded-full blur-[80px] sm:blur-[120px] -z-10" />
        
        <div className="max-w-6xl mx-auto grid lg:grid-cols-2 gap-12 sm:gap-16 items-center text-center lg:text-left">
          <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.6 }}>
            <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-primary-container text-primary text-[10px] sm:text-xs font-bold tracking-widest uppercase mb-6 border border-primary/20">
               <Sparkles size={14} />
               1.0 · For Android
            </div>
            <h1 className="text-4xl sm:text-6xl lg:text-7xl font-extrabold tracking-tighter mb-6 leading-[1.1]">
              Notes that stay <span className="text-primary italic">out of your way</span>.
            </h1>
            <p className="text-base sm:text-xl text-on-surface-variant mb-8 max-w-lg mx-auto lg:mx-0 leading-relaxed font-medium">
              A simple, private notes app built around one Pill. Your notes sit in folders on one calm page; swipe the Pill to search them, find Settings or Recently Deleted, and tap + to start writing. Real formatting, lists that behave, and everything kept on your phone in a format you can back up and take anywhere.
            </p>
            <div className="flex flex-col sm:flex-row justify-center lg:justify-start gap-4">
              <a href="#download" className="flex items-center justify-center gap-2 bg-primary text-on-primary px-8 py-4 rounded-2xl font-bold text-lg hover:shadow-xl hover:shadow-primary/25 transition-all active:scale-95">
                <Download size={20} />
                Download APK
              </a>
              <a href={GITHUB_URL} className="flex items-center justify-center gap-2 bg-surface-container-high px-8 py-4 rounded-2xl font-bold text-lg hover:bg-surface-container-highest transition-all border border-outline-variant">
                View on GitHub
                <ChevronRight size={20} />
              </a>
            </div>
          </motion.div>

          <motion.div 
            initial={{ opacity: 0, scale: 0.9 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={{ duration: 0.8, delay: 0.2 }}
            className="relative flex justify-center lg:justify-end mt-8 lg:mt-0"
          >
            <DeviceMockup src={getAssetPath("/images/home.jpg")} color="blue" />
          </motion.div>
        </div>
      </section>

      {/* Quick Specs Section */}
      <section className="py-16 sm:py-24 border-y border-outline-variant bg-surface-container">
        <div className="max-w-6xl mx-auto px-6 grid sm:grid-cols-2 md:grid-cols-3 gap-8 sm:gap-12">
           <FeatureBrief icon={<Zap size={24} />} title="Gesture First" desc="One Pill, a few swipes, nothing in the way of your notes." />
           <FeatureBrief icon={<Palette size={24} />} title="Material You" desc="Colors from your accent or your wallpaper." />
           <FeatureBrief icon={<Shield size={24} />} title="Private by Default" desc="No accounts, no tracking. Your notes never leave your phone unless you share them." />
        </div>
      </section>

      {/* Detailed Features: Alternating Sections */}
      <div className="space-y-24 sm:space-y-32 py-24 sm:py-32">
        
        {/* The Pill */}
        <FeatureDetail 
          image={getAssetPath("/images/editor.jpg")}
          tag="Writing"
          tagColor="text-primary bg-primary-container border-primary/20"
          title="Real formatting. One small Pill."
          description="In a note, the Pill becomes your toolbar: text styles, bold, checklists, bullets and numbers are a tap away, and it docks above the keyboard while you type. Swipe it right to undo, left to redo, and up to go back to your notes."
          bullets={[
            "Swipe right to undo, left to redo",
            "Title, Heading and Subheading styles",
            "Docks above the keyboard",
            "Saves as you type"
          ]}
          imageSide="right"
          glowColor="blue"
        />

        {/* Formatting */}
        <FeatureDetail 
          image={getAssetPath("/images/format.jpg")}
          tag="Formatting"
          tagColor="text-tertiary bg-tertiary-container border-tertiary/20"
          title="Everything on one panel."
          description="Tap Aa for the whole toolbox: styles, bold, italic, underline and strikethrough, any text size, text colours and highlights, alignment, indents and lists. It slides up where the keyboard was, and every change shows as you make it."
          bullets={[
            "Any text size you type",
            "Text colours and highlights",
            "Left, centre and right alignment",
            "Six levels of indent"
          ]}
          imageSide="left"
          glowColor="purple"
        />

        {/* Lists and blocks */}
        <FeatureDetail 
          image={getAssetPath("/images/insert.jpg")}
          tag="Structure"
          tagColor="text-secondary bg-secondary-container border-secondary/20"
          title="Lists that behave."
          description="Bullets, numbers and checklists carry on with Enter and end on an empty line, like every editor you know. The + adds dividers, quotes, code blocks and callouts, and ticked items can move to the bottom of their list."
          bullets={[
            "Press Enter twice to end any list or block",
            "Tap a checkbox to tick it off",
            "Dividers, quotes, code and callouts",
            "Exports to Markdown, and back"
          ]}
          imageSide="right"
          glowColor="cyan"
        />

        {/* Organising */}
        <FeatureDetail 
          image={getAssetPath("/images/menu.jpg")}
          tag="Organization"
          tagColor="text-tertiary bg-tertiary-container border-tertiary/20"
          title="Hold a note. Put it anywhere."
          description="Pick a folder from its chip at the top. Hold a note to pick it up and drag it where you want it, or let go without moving for its menu: pin it, share it, move it to a folder, rename it, give it a colour, export it or duplicate it."
          bullets={[
            "Folders with a chip each",
            "Pins, colours and manual order",
            "Share as text or Markdown",
            "Undo after every delete"
          ]}
          imageSide="left"
          glowColor="pink"
        />

        {/* Customization */}
        <FeatureDetail 
          image={getAssetPath("/images/settings.jpg")}
          tag="Aesthetic"
          tagColor="text-secondary bg-secondary-container border-secondary/20"
          title="Deeply, truly yours."
          description="Pick an accent (or use your wallpaper) and the whole Material 3 colour scheme follows. Choose staggered cards, a grid or a list; square, soft or round corners; the spacing, the font and the size of your notes' text."
          bullets={[
            "Material You dynamic colour",
            "Staggered, Grid or List layouts",
            "Rounded Google Sans Flex option",
            "Pure Black for OLED screens"
          ]}
          imageSide="right"
          glowColor="orange"
        />

        <YourData />

      </div>

      {/* Download CTA */}
      <section id="download" className="py-20 sm:py-40 bg-primary-container text-on-primary-container rounded-[2rem] sm:rounded-[4rem] mx-4 mb-20 relative overflow-hidden">
        <div className="absolute top-0 left-0 w-full h-full opacity-40 pointer-events-none">
           <div className="absolute top-[-10%] left-[-10%] w-[40%] h-[40%] bg-primary rounded-full blur-[120px]" />
           <div className="absolute bottom-[-10%] right-[-10%] w-[40%] h-[40%] bg-tertiary rounded-full blur-[120px]" />
        </div>
        <div className="max-w-3xl mx-auto px-6 text-center relative z-10">
          <h2 className="text-4xl sm:text-6xl font-extrabold mb-8 tracking-tighter">Ready to write?</h2>
          <p className="text-lg sm:text-xl opacity-70 mb-12 font-medium">
            Download the latest APK for Android and keep your notes in an app that stays out of your way.
          </p>
          <div className="flex flex-col items-center gap-6">
             <a href={`${GITHUB_URL}/releases`} className="group flex items-center gap-4 bg-primary text-on-primary px-8 py-4 sm:px-12 sm:py-6 rounded-2xl sm:rounded-3xl font-black text-xl sm:text-2xl hover:scale-105 active:scale-95 transition-all shadow-2xl shadow-primary/30">
               <Download size={28} />
               Install Latest APK
             </a>
             <span className="text-sm opacity-40 font-bold tracking-widest uppercase">Requires Android 8.0+</span>
          </div>
        </div>
      </section>

      {/* Footer */}
      <Footer />
    </main>
  );
}

/** Your data: where notes live and the ways out (backups, Markdown, sharing). */
function YourData() {
  const ways = ["One-file backups", "Markdown export", "Markdown & text import", "Share as text", "Share as Markdown", "Send text from any app"];
  return (
    <section className="max-w-6xl mx-auto px-6">
      <motion.div
        initial={{ opacity: 0, y: 20 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }} transition={{ duration: 0.6 }}
        className="rounded-[2rem] sm:rounded-[3rem] bg-surface-container p-8 sm:p-14 grid lg:grid-cols-2 gap-10 items-center"
      >
        <div className="space-y-6 text-center lg:text-left">
          <span className="inline-flex items-center gap-2 px-4 py-1.5 rounded-full text-[10px] sm:text-xs font-bold tracking-[0.2em] uppercase bg-tertiary-container text-on-tertiary-container">
            <FolderOpen size={14} /> Your Data
          </span>
          <h3 className="text-3xl sm:text-4xl lg:text-5xl font-extrabold tracking-tighter leading-tight">Your notes, your files.</h3>
          <p className="text-base sm:text-xl text-on-surface-variant leading-relaxed font-medium">
            Notes stay on your phone, in a plain format. Back everything up to one file, export every note as Markdown for any other notes app, or bring Markdown and text files in. Nothing is locked away.
          </p>
        </div>
        <div className="flex flex-wrap gap-3 justify-center lg:justify-start">
          {ways.map((name) => (
            <span key={name} className="inline-flex items-center gap-2 pl-3 pr-4 py-2.5 rounded-full bg-surface-container-highest text-on-surface font-bold text-sm sm:text-base">
              <Check size={16} className="text-primary" /> {name}
            </span>
          ))}
        </div>
      </motion.div>
    </section>
  );
}

function FeatureBrief({ icon, title, desc }: { icon: React.ReactNode, title: string, desc: string }) {
  return (
    <div className="space-y-3">
      <div className="bg-primary-container text-on-primary-container w-12 h-12 flex items-center justify-center rounded-full mb-4">
        {icon}
      </div>
      <h4 className="font-bold text-lg tracking-tight">{title}</h4>
      <p className="text-on-surface-variant leading-relaxed font-medium">{desc}</p>
    </div>
  );
}

function FeatureDetail({ image, tag, tagColor, title, description, bullets, imageSide, glowColor }: any) {
  const content = (
    <motion.div 
      initial={{ opacity: 0, x: imageSide === 'right' ? -30 : 30 }}
      whileInView={{ opacity: 1, x: 0 }}
      viewport={{ once: true }}
      transition={{ duration: 0.8 }}
      className="flex-1 space-y-8 text-center lg:text-left"
    >
      <div className="space-y-4">
        <span className={`inline-block px-4 py-1.5 rounded-full text-[10px] sm:text-xs font-bold tracking-[0.2em] uppercase border ${tagColor}`}>
          {tag}
        </span>
        <h3 className="text-3xl sm:text-4xl lg:text-5xl font-extrabold tracking-tighter leading-tight">{title}</h3>
        <p className="text-base sm:text-xl text-on-surface-variant leading-relaxed font-medium">
          {description}
        </p>
      </div>
      <ul className="space-y-4 inline-block lg:block text-left">
        {bullets.map((b: string) => (
          <li key={b} className="flex items-center gap-3 font-bold text-on-surface text-sm sm:text-base">
            <div className={`w-2 h-2 rounded-full shrink-0 ${
              glowColor === 'blue' ? 'bg-primary' : 
              glowColor === 'purple' ? 'bg-tertiary' : 
              glowColor === 'pink' ? 'bg-tertiary' : 'bg-secondary'
            }`} />
            {b}
          </li>
        ))}
      </ul>
    </motion.div>
  );

  const imgBlock = (
    <div className="flex-1 flex justify-center mt-8 lg:mt-0">
      <DeviceMockup src={image} color={glowColor} />
    </div>
  );

  return (
    <section className="max-w-6xl mx-auto px-6">
      <div className={`flex flex-col ${imageSide === 'right' ? 'lg:flex-row' : 'lg:flex-row-reverse'} items-center gap-12 lg:gap-20`}>
        {content}
        {imgBlock}
      </div>
    </section>
  );
}

function DeviceMockup({ src, color }: { src: string, color?: string }) {
  const glowStyles: any = {
    blue: "bg-primary/20 shadow-primary/30",
    purple: "bg-tertiary/20 shadow-tertiary/30",
    pink: "bg-tertiary/20 shadow-tertiary/30",
    orange: "bg-secondary/20 shadow-secondary/30",
    cyan: "bg-secondary/20 shadow-secondary/30",
  };

  return (
    <div className="relative group">
      <div className={`absolute -inset-10 ${color ? glowStyles[color].split(' ')[0] : 'bg-primary-container'} rounded-full blur-[80px] group-hover:opacity-100 transition-opacity opacity-50`} />
      <div className={`relative w-[220px] sm:w-[280px] bg-black rounded-[2rem] sm:rounded-[2.5rem] p-2 sm:p-3 shadow-2xl border-[4px] sm:border-[6px] border-[#1a1a1a] overflow-hidden transition-transform duration-500 group-hover:scale-105 group-hover:-rotate-1`}>
        <img 
          src={src} 
          alt="mi. App Screenshot" 
          className="w-full h-auto block rounded-[1.4rem] sm:rounded-[1.8rem]"
        />
      </div>
    </div>
  );
}
