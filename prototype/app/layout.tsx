import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "随身工具箱 · 交互原型",
  description: "个人离线工具箱，轻松完成电费分摊与费用趋势查看。",
  other: {
    "codex-preview": "development",
  },
  icons: {
    icon: "/favicon.svg",
    shortcut: "/favicon.svg",
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="zh-CN">
      <body className="antialiased">{children}</body>
    </html>
  );
}
