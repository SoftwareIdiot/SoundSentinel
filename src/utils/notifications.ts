/**
 * Handles Web Notifications, audio beeps, haptics, and Screen Wake Lock.
 */

class NotificationService {
  private wakeLockSentinel: any = null;
  private audioContext: AudioContext | null = null;
  private keepAliveOscillator: OscillatorNode | null = null;

  async requestPermission(): Promise<NotificationPermission> {
    if (typeof window === 'undefined' || !('Notification' in window)) {
      return 'denied';
    }
    try {
      const permission = await Notification.requestPermission();
      return permission;
    } catch {
      return 'denied';
    }
  }

  getPermissionState(): NotificationPermission {
    if (typeof window === 'undefined' || !('Notification' in window)) {
      return 'denied';
    }
    return Notification.permission;
  }

  sendDetectionNotification(decibels: number, triggerCount: number = 1): void {
    if (typeof window === 'undefined' || !('Notification' in window)) return;
    if (Notification.permission !== 'granted') return;

    try {
      const title = '🚨 SoundSentinel CCTV Alert';
      const body = `Sound detected (${decibels} dB)! Recording started (10s post-roll active). Re-triggered: ${triggerCount}`;
      const options: NotificationOptions = {
        body,
        icon: '/icon.svg',
        badge: '/icon.svg',
        tag: 'cctv-sound-detected',
        requireInteraction: false,
        silent: false,
      };

      new Notification(title, options);
    } catch (e) {
      console.warn('Failed to send web notification', e);
    }
  }

  sendStorageWarningNotification(usedPercentage: number, usedMB: string, quotaMB: number): void {
    if (typeof window === 'undefined' || !('Notification' in window)) return;
    if (Notification.permission !== 'granted') return;

    try {
      const title = '⚠️ SoundSentinel Storage Warning';
      const body = `Storage is at ${usedPercentage}% capacity (${usedMB} of ${quotaMB} MB). Oldest recordings will be automatically overwritten when full!`;
      const options: NotificationOptions = {
        body,
        icon: '/icon.svg',
        badge: '/icon.svg',
        tag: 'cctv-storage-warning',
        requireInteraction: true,
      };

      new Notification(title, options);
    } catch (e) {
      console.warn('Failed to send storage notification', e);
    }
  }

  triggerHaptic(): void {
    if (typeof window !== 'undefined' && 'navigator' in window && 'vibrate' in navigator) {
      try {
        navigator.vibrate([100, 50, 100]);
      } catch {
        // ignore
      }
    }
  }

  playAlertBeep(): void {
    try {
      const AudioCtx = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
      if (!AudioCtx) return;
      const ctx = new AudioCtx();
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();

      osc.type = 'sine';
      osc.frequency.setValueAtTime(880, ctx.currentTime); // A5
      osc.frequency.exponentialRampToValueAtTime(440, ctx.currentTime + 0.15);

      gain.gain.setValueAtTime(0.2, ctx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.01, ctx.currentTime + 0.15);

      osc.connect(gain);
      gain.connect(ctx.destination);

      osc.start();
      osc.stop(ctx.currentTime + 0.16);
      osc.onended = () => {
        ctx.close().catch(() => {});
      };
    } catch {
      // Audio autoplay restrictions or headless env
    }
  }

  async acquireWakeLock(): Promise<boolean> {
    if (typeof navigator !== 'undefined' && 'wakeLock' in navigator) {
      try {
        this.wakeLockSentinel = await (navigator as any).wakeLock.request('screen');
        this.wakeLockSentinel.addEventListener('release', () => {
          this.wakeLockSentinel = null;
        });
        return true;
      } catch {
        return false;
      }
    }
    return false;
  }

  releaseWakeLock(): void {
    if (this.wakeLockSentinel) {
      try {
        this.wakeLockSentinel.release();
      } catch {
        // ignore
      }
      this.wakeLockSentinel = null;
    }
  }

  /**
   * Starts a subtle inaudible keep-alive audio session
   * Keeps mobile OS audio session active when browser is backgrounded.
   */
  startKeepAliveAudio(): void {
    try {
      if (this.keepAliveOscillator) return;
      const AudioCtx = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
      if (!AudioCtx) return;
      this.audioContext = new AudioCtx();
      if (this.audioContext.state === 'suspended') {
        this.audioContext.resume();
      }
      const osc = this.audioContext.createOscillator();
      const gain = this.audioContext.createGain();
      // Near ultrasonic or zero gain
      osc.frequency.setValueAtTime(20000, this.audioContext.currentTime);
      gain.gain.setValueAtTime(0.0001, this.audioContext.currentTime);
      osc.connect(gain);
      gain.connect(this.audioContext.destination);
      osc.start();
      this.keepAliveOscillator = osc;
    } catch {
      // ignore
    }
  }

  stopKeepAliveAudio(): void {
    try {
      if (this.keepAliveOscillator) {
        this.keepAliveOscillator.stop();
        this.keepAliveOscillator.disconnect();
        this.keepAliveOscillator = null;
      }
      if (this.audioContext) {
        this.audioContext.close().catch(() => {});
        this.audioContext = null;
      }
    } catch {
      // ignore
    }
  }
}

export const notificationService = new NotificationService();
