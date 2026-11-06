import { useState } from "react";
import { Link } from "react-router";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { authApi } from "@/services/endpoints";

const schema = z.object({ email: z.email("Informe um e-mail válido.") });
type FormValues = z.infer<typeof schema>;

export function ForgotPasswordPage() {
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const form = useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: { email: "" } });

  const onSubmit = form.handleSubmit(async (values) => {
    setError(null);
    try {
      const res = await authApi.forgotPassword(values.email);
      setMessage(res.message);
    } catch {
      setError("Não foi possível processar o pedido. Tente novamente em instantes.");
    }
  });

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Recuperar senha</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Informe o e-mail da sua conta. Se ele existir, enviaremos um link válido por 1 hora.
        </p>
      </div>
      {message ? (
        <Alert role="status">
          <AlertDescription>
            {message} Em ambientes sem SMTP configurado, o link é registrado no log do servidor.
          </AlertDescription>
        </Alert>
      ) : (
        <form onSubmit={onSubmit} className="space-y-4" noValidate>
          {error && (
            <Alert variant="destructive" role="alert">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          )}
          <div className="space-y-2">
            <Label htmlFor="email">E-mail</Label>
            <Input
              id="email"
              type="email"
              autoComplete="email"
              {...form.register("email")}
              aria-invalid={!!form.formState.errors.email}
            />
            {form.formState.errors.email && (
              <p className="text-xs text-destructive">{form.formState.errors.email.message}</p>
            )}
          </div>
          <Button type="submit" className="w-full" disabled={form.formState.isSubmitting}>
            {form.formState.isSubmitting && <Loader2 className="size-4 animate-spin" aria-hidden />}
            Enviar link de redefinição
          </Button>
        </form>
      )}
      <p className="text-center text-sm text-muted-foreground">
        <Link to="/login" className="font-medium text-primary hover:underline">
          Voltar para o login
        </Link>
      </p>
    </div>
  );
}
