/** One entry of a right-click menu. */
export interface MenuItem {
  label: string;
  run: () => void;
  danger?: boolean;
}
