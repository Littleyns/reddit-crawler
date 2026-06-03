"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { LoaderCircle, LockKeyhole } from "lucide-react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { useLogin } from "@/hooks/use-reddit-crawler";

const authSchema = z.object({
  email: z.string().email("Enter a valid email address"),
  password: z.string().min(8, "Password must be at least 8 characters"),
});

type AuthValues = z.infer<typeof authSchema>;

export function AuthForm() {
  const loginMutation = useLogin();
  const form = useForm<AuthValues>({
    resolver: zodResolver(authSchema),
    defaultValues: {
      email: "amina@arabtooling.com",
      password: "password123",
    },
  });

  return (
    <section className="panel rounded-[28px] border-white/40 p-6">
      <div className="flex items-center gap-3">
        <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-[var(--color-accent-soft)] text-[var(--color-accent-strong)]">
          <LockKeyhole className="h-5 w-5" />
        </div>
        <div>
          <p className="text-xs uppercase tracking-[0.28em] text-[var(--color-muted)]">
            Session Access
          </p>
          <h3 className="mt-1 text-xl font-semibold">Authentication</h3>
        </div>
      </div>

      <form
        className="mt-6 space-y-4"
        onSubmit={form.handleSubmit(async (values) => {
          await loginMutation.mutateAsync(values);
        })}
      >
        <label className="flex flex-col gap-2">
          <span className="text-sm font-medium">Email</span>
          <input
            {...form.register("email")}
            className="rounded-2xl border border-[var(--color-border)] bg-white/80 px-4 py-3 outline-none focus:border-[var(--color-accent)]"
          />
          <span className="text-sm text-[var(--color-danger)]">
            {form.formState.errors.email?.message}
          </span>
        </label>

        <label className="flex flex-col gap-2">
          <span className="text-sm font-medium">Password</span>
          <input
            type="password"
            {...form.register("password")}
            className="rounded-2xl border border-[var(--color-border)] bg-white/80 px-4 py-3 outline-none focus:border-[var(--color-accent)]"
          />
          <span className="text-sm text-[var(--color-danger)]">
            {form.formState.errors.password?.message}
          </span>
        </label>

        <button
          type="submit"
          disabled={loginMutation.isPending}
          className="inline-flex w-full items-center justify-center gap-2 rounded-2xl bg-[var(--color-surface-dark)] px-5 py-3 text-sm font-medium text-white disabled:opacity-60"
        >
          {loginMutation.isPending ? (
            <LoaderCircle className="h-4 w-4 animate-spin" />
          ) : (
            <LockKeyhole className="h-4 w-4" />
          )}
          Sign in
        </button>
      </form>

      {loginMutation.data ? (
        <p className="mt-4 text-sm text-[var(--color-accent-strong)]">
          Session active for {loginMutation.data.user.name} until{" "}
          {new Date(loginMutation.data.sessionExpiresAt).toLocaleTimeString("en-US", {
            hour: "numeric",
            minute: "2-digit",
          })}
          .
        </p>
      ) : null}
    </section>
  );
}
