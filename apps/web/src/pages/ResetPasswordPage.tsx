import { useState } from "react";
import { Link, useNavigate, useParams } from "react-router";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Loader2 } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { ApiError } from "@/services/api";
import { authApi } from "@/services/endpoints";

const schema = z
  .object({
    password: z.string().min(10, "A senha deve ter pelo menos 10 caracteres."),
    confirm: z.string(),
  })
  .refine((v) => v.password === v.confirm, { message: "As senhas não coincidem.", path: ["confirm"] });
type FormValues = z.infer<typeof schema>;

export function ResetPasswordPage() {
  const { token = "" } = useParams();
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);
  const form = useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: { password: "", confirm: "" } });

  const onSubmit = form.handleSubmit(async (values) => {
    setError(null);
    try {
      const res = await authApi.resetPassword(token, values.password);
      toast.success(res.message);
      navigate("/login", { replace: true });
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Não foi possível redefinir a senha.");
    }
  });

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Definir nova senha</h1>
        <p className="mt-1 text-sm text-muted-foreground">O link é de uso único e expira em 1 hora.</p>
      </div>
      {error && (
        <Alert variant="destructive" role="alert">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}
      <form onSubmit={onSubmit} className="space-y-4" noValidate>
        <div className="space-y-2">
          <Label htmlFor="password">Nova senha (mínimo 10 caracteres)</Label>
          <Input
            id="password"
            type="password"
            autoComplete="new-password"
            {...form.register("password")}
            aria-invalid={!!form.formState.errors.password}
          />
          {form.formState.errors.password && (
            <p className="text-xs text-destructive">{form.formState.errors.password.message}</p>
          )}
        </div>
        <div className="space-y-2">
          <Label htmlFor="confirm">Confirmar senha</Label>
          <Input
            id="confirm"
            type="password"
            autoComplete="new-password"
            {...form.register("confirm")}
            aria-invalid={!!form.formState.errors.confirm}
          />
          {form.formState.errors.confirm && (
            <p className="text-xs text-destructive">{form.formState.errors.confirm.message}</p>
          )}
        </div>
        <Button type="submit" className="w-full" disabled={form.formState.isSubmitting}>
          {form.formState.isSubmitting && <Loader2 className="size-4 animate-spin" aria-hidden />}
          Redefinir senha
        </Button>
      </form>
      <p className="text-center text-sm text-muted-foreground">
        <Link to="/login" className="font-medium text-primary hover:underline">
          Voltar para o login
        </Link>
      </p>
    </div>
  );
}
