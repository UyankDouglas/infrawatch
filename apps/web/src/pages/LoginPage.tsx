import { useState } from "react";
import { Link, useLocation, useNavigate } from "react-router";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { useAuth } from "@/features/auth/AuthProvider";
import { ApiError } from "@/services/api";
import { authApi } from "@/services/endpoints";

const schema = z.object({
  email: z.email("Informe um e-mail válido."),
  password: z.string().min(1, "Informe a senha."),
});
type FormValues = z.infer<typeof schema>;

export function LoginPage() {
  const { setUser } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [error, setError] = useState<string | null>(null);
  const form = useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: { email: "", password: "" } });

  const onSubmit = form.handleSubmit(async (values) => {
    setError(null);
    try {
      const me = await authApi.login(values.email, values.password);
      setUser(me);
      const from = (location.state as { from?: string } | null)?.from;
      navigate(from && from !== "/login" ? from : "/", { replace: true });
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) setError("E-mail ou senha incorretos.");
      else if (e instanceof ApiError && e.status === 429) setError("Muitas tentativas. Aguarde um minuto.");
      else setError("Não foi possível entrar. Verifique a conexão com a API.");
    }
  });

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Entrar no InfraWatch</h1>
        <p className="mt-1 text-sm text-muted-foreground">Use sua conta para acessar o painel de monitoramento.</p>
      </div>
      {error && (
        <Alert variant="destructive" role="alert">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}
      <form onSubmit={onSubmit} className="space-y-4" noValidate>
        <div className="space-y-2">
          <Label htmlFor="email">E-mail</Label>
          <Input
            id="email"
            type="email"
            autoComplete="email"
            placeholder="voce@empresa.com"
            {...form.register("email")}
            aria-invalid={!!form.formState.errors.email}
          />
          {form.formState.errors.email && (
            <p className="text-xs text-destructive">{form.formState.errors.email.message}</p>
          )}
        </div>
        <div className="space-y-2">
          <Label htmlFor="password">Senha</Label>
          <Input
            id="password"
            type="password"
            autoComplete="current-password"
            {...form.register("password")}
            aria-invalid={!!form.formState.errors.password}
          />
          {form.formState.errors.password && (
            <p className="text-xs text-destructive">{form.formState.errors.password.message}</p>
          )}
        </div>
        <Button type="submit" className="w-full" disabled={form.formState.isSubmitting}>
          {form.formState.isSubmitting && <Loader2 className="size-4 animate-spin" aria-hidden />}
          Entrar
        </Button>
      </form>
      <p className="text-center text-sm text-muted-foreground">
        Ainda não tem conta?{" "}
        <Link to="/cadastro" className="font-medium text-primary hover:underline">
          Criar conta e organização
        </Link>
      </p>
      <div className="rounded-md border border-border bg-card/60 p-3 text-xs text-muted-foreground">
        <p className="font-medium text-foreground">Ambiente de demonstração</p>
        <p className="mt-1">
          Com a seed ativa: <code className="rounded bg-muted px-1">admin@demo.infrawatch.local</code> · senha{" "}
          <code className="rounded bg-muted px-1">InfraWatch@2026</code>
        </p>
      </div>
    </div>
  );
}
