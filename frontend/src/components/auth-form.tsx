"use client";

import { LockKeyhole, Loader } from "lucide-react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useLogin } from "@/hooks/use-reddit-crawler";

const authSchema = z.object({
  email: z.string().email(),
  password: z.string().min(8),
});
type AuthValues = z.infer<typeof authSchema>;

export function AuthForm() {
  const loginMutation = useLogin();
  const form = useForm<AuthValues>({
    resolver: zodResolver(authSchema),
    defaultValues: { email: "amina@arabtooling.com", password: "password123" },
  });

  return (
    <section className="panel-sq-dense rounded-none overflow-hidden border border-[var(--color-border)] bg-[var(--color-surface-mid)] px-3 py-2">
      <div className="flex items-center gap-2 mb-2">
        <div className="flex h-6 w-6 shrink-0 items-center justify-center bg-[var(--color-accent)]/10 border border-[var(--color-border-muted)] text-[var(--color-accent-text)] rounded-none">
          <LockKeyhole className="h-3.5 w-3.5" />
        </div>
        <div>
          <span className="section-label block mb-0">Session Access</span>
          <h3 className="text-[11px] font-semibold text-[var(--color-fg-primary)]">Authentication</h3>
        </div>
      </div>

      <form className="flex flex-col gap-2" onSubmit={form.handleSubmit(async (v) => loginMutation.mutate(v))}>
        {[
          { name: "email" as const, label: "Email", type: "email" },
          { name: "password" as const, label: "Password", type: "password" },
        ].map(({ name, label, type }) => (
          <label key={name} className="flex flex-col gap-0.5">
            <span className="text-[9px] font-semibold text-[var(--color-fg-muted)] uppercase tracking-wider">{label}</span>
            <input
              {...form.register(name, { required: true })}
              type={type}
              className="input-sq rounded-none"
            />
            {form.formState.errors[name] && (
              <span className="text-[9px] text-[var(--color-danger-text)]">{form.formState.errors[name]?.message}</span>
            )}
          </label>
        ))}

        <button
          type="submit"
          disabled={loginMutation.isPending}
          className="btn-sq btn-sq-primary w-full py-[4px] mt-1 rounded-none disabled:opacity-50 flex items-center justify-center gap-1.5 text-[10px]"
        >
          {loginMutation.isPending ? <Loader className="h-3.5 w-3.5 animate-spin" /> : <LockKeyhole className="h-3.5 w-3.5" />}
          Sign In
        </button>
      </form>

      {loginMutation.data && (
        <p className="text-[9px] text-[var(--color-success-text)] mt-2 bg-[var(--color-success-bg)] px-2 py-1 border border-[var(--color-success-border)] rounded-none leading-relaxed">
          Session active for {loginMutation.data.user.name} until{" "}
          {new Date(loginMutation.data.sessionExpiresAt).toLocaleTimeString("en-US", {
            hour: "numeric", minute: "2-digit"
          })}.
        </p>
      )}
    </section>
  );
}
