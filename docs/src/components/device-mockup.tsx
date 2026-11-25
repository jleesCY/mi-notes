import { getAssetPath } from "@/lib/asset";

const GLOW: Record<string, string> = {
  blue: "bg-primary/20",
  purple: "bg-tertiary/20",
  pink: "bg-tertiary/20",
  orange: "bg-secondary/20",
  cyan: "bg-secondary/20",
  green: "bg-secondary/20",
};

/** A screenshot (a path in public/) in a phone frame, with a soft glow of the given colour behind it. */
export function DeviceMockup({ src, color }: { src: string; color?: string }) {
  return (
    <div className="relative group flex justify-center">
      <div className={`absolute -inset-10 ${(color && GLOW[color]) || "bg-primary-container"} rounded-full blur-[80px] group-hover:opacity-100 transition-opacity opacity-50`} />
      <div className="relative w-[220px] sm:w-[280px] bg-black rounded-[2rem] sm:rounded-[2.5rem] p-2 sm:p-3 shadow-2xl border-[4px] sm:border-[6px] border-[#1a1a1a] overflow-hidden transition-transform duration-500 group-hover:scale-105 group-hover:-rotate-1">
        <img src={getAssetPath(src)} alt="mi. App Screenshot" className="w-full h-auto block rounded-[1.4rem] sm:rounded-[1.8rem]" />
      </div>
    </div>
  );
}
