"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { LoaderCircle, LockKeyhole } from "lucide-react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { useLogin } from "@/hooks/use-reddit-crawler";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { FormActions, FormSection } from "@/components/ui/form";
import { Input } from "@/components/ui/input";

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
    <Card variant="spotlight">
      <CardHeader
        title="Authentication"
        description="Use the same field primitives and validation feedback used across settings and controls."
      >
        <p className="mt-3 text-xs uppercase tracking-[0.28em] text-[var(--ds-text-muted)]">
          Session Access
        </p>
      </CardHeader>

      <CardContent>
        <form
          className="space-y-4"
          onSubmit={form.handleSubmit(async (values) => {
            await loginMutation.mutateAsync(values);
          })}
        >
          <FormSection title="Operator credentials">
            <Input
              label="Email"
              autoComplete="email"
              {...form.register("email")}
              error={form.formState.errors.email?.message}
            />
            <Input
              label="Password"
              type="password"
              autoComplete="current-password"
              {...form.register("password")}
              error={form.formState.errors.password?.message}
            />
          </FormSection>

          <FormActions>
            <Button
              type="submit"
              loading={loginMutation.isPending}
              leadingIcon={
                loginMutation.isPending ? (
                  <LoaderCircle className="h-4 w-4 animate-spin" />
                ) : (
                  <LockKeyhole className="h-4 w-4" />
                )
              }
            >
              Sign in
            </Button>
          </FormActions>
        </form>

        {loginMutation.data ? (
          <p className="mt-4 text-sm text-[var(--ds-success-500)]">
            Session active for {loginMutation.data.user.name} until{" "}
            {new Date(loginMutation.data.sessionExpiresAt).toLocaleTimeString("en-US", {
              hour: "numeric",
              minute: "2-digit",
            })}
            .
          </p>
        ) : null}
      </CardContent>
    </Card>
  );
}
