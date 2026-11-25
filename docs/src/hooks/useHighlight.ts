"use client";

import { useEffect } from 'react';
import { usePathname } from 'next/navigation';

/** Scrolls [el] into view and flashes it (the highlight-section animation, restarted if it's already run). */
export function highlight(el: HTMLElement) {
  el.scrollIntoView({ behavior: 'smooth', block: 'start' });
  el.classList.remove('highlight-section');
  void el.offsetWidth; // a reflow, so the animation starts again
  el.classList.add('highlight-section');
}

export function useHighlight() {
  const pathname = usePathname();

  useEffect(() => {
    const handleHashHighlight = () => {
      const hash = window.location.hash.replace('#', '');
      if (hash) {
        const element = document.getElementById(hash);
        if (element) {
          // Add a small delay to ensure page is rendered and scroll-margin is respected
          setTimeout(() => highlight(element), 500);
        }
      }
    };

    handleHashHighlight();
    // Also listen for hash changes (e.g. from browser back/forward)
    window.addEventListener('hashchange', handleHashHighlight);
    return () => window.removeEventListener('hashchange', handleHashHighlight);
  }, [pathname]);
}
