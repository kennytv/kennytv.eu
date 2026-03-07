import type { Project } from './types';

export const PROJECTS: Project[] = [
  {
    title: 'PaperMC',
    description:
      'I am a core team member of <a href="https://papermc.io/" target="_blank" rel="noopener">PaperMC</a>. Its main project, <a href="https://github.com/PaperMC/Paper" target="_blank" rel="noopener">Paper</a>, is the most widely used, performant, and stable Minecraft server software available.',
    href: 'https://papermc.io/',
    icon: 'send',
  },
  {
    title: 'ViaVersion',
    description:
      'I maintain <a href="https://github.com/ViaVersion" target="_blank" rel="noopener">ViaVersion</a>, allowing both older and newer Minecraft client versions to connect to almost any server; why lose players after a Minecraft update?',
    href: 'https://github.com/ViaVersion',
    icon: 'link',
  },
  {
    title: 'Hangar',
    description:
      '<a href="https://hangar.papermc.io" target="_blank" rel="noopener">Hangar</a> is a plugin repository for Paper and Velocity plugins.',
    href: 'https://hangar.papermc.io',
    icon: 'plane',
  },
];

export const SMALLER_PROJECTS: Project[] = [
  {
    title: 'Item Command Converter',
    description: 'Convert your old NBT item commands to the new 1.20.5 format.',
    href: 'https://docs.papermc.io/misc/tools/item-command-converter',
    icon: 'settings',
  },
  {
    title: 'Entity Data',
    description:
      'Browse and compare Minecraft entity data fields across versions.',
    href: '/entity-data',
    icon: 'database',
  },
];

export const SOCIAL_LINKS = [
  { href: 'https://bsky.app/profile/kennytv.eu', label: 'Bluesky', icon: 'bluesky' },
  { href: 'https://github.com/kennytv', label: 'GitHub', icon: 'github' },
  { href: 'https://twitch.com/kennytvn', label: 'Twitch', icon: 'twitch' },
  { href: 'https://twitter.com/kennytvn', label: 'Twitter', icon: 'twitter' },
] as const;
