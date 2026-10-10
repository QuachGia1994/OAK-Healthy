import Link from "next/link";
export default function NotFound() {
  return (<div className="py-24 text-center"><p className="font-mono text-sm text-neutral-400">404</p><h1 className="mt-2 text-2xl font-semibold">Page not found</h1><Link href="/" className="btn mt-6">Back to Today</Link></div>);
}
