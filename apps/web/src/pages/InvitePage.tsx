import { Link, useNavigate, useParams } from "react-router";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Loader2, MailCheck, MailX } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Skeleton } from "@/components/ui/skeleton";
import { useAuth } from "@/features/auth/AuthProvider";
import { ApiError } from "@/services/api";
import { organizationApi } from "@/services/endpoints";

/**
 * Tela pública de aceite de convite:
 *  - sem login → oferece criar conta (com o token) ou entrar com conta existente e voltar;
 *  - logado com o mesmo e-mail → aceita e troca a organização ativa.
 */
export function InvitePage() {
  const { token = "" } = useParams();
  const navigate = useNavigate();
  const { user, isLoading, refresh } = useAuth();
  const invite = useQuery({ queryKey: ["invite", token], queryFn: () => organizationApi.describeInvite(token) });

  const accept = useMutation({
    mutationFn: () => organizationApi.acceptInvite(token),
    onSuccess: async (res) => {
      await refresh();
      toast.success(`Você entrou em ${res.organizationName}.`);
      navigate("/", { replace: true });
    },
    onError: (e) => toast.error(e instanceof ApiError ? e.message : "Não foi possível aceitar o convite."),
  });

  if (invite.isLoading || isLoading) return <Skeleton className="h-40 w-full" />;
  const data = invite.data;

  if (!data || !data.valid) {
    return (
      <div className="space-y-6">
        <div className="flex items-center gap-3">
          <MailX className="size-8 text-destructive" aria-hidden />
          <h1 className="text-2xl font-semibold tracking-tight">Convite indisponível</h1>
        </div>
        <Alert variant="destructive" role="alert">
          <AlertDescription>{data?.reason ?? "Convite inválido."}</AlertDescription>
        </Alert>
        <Link to="/login" className="text-sm font-medium text-primary hover:underline">
          Ir para o login
        </Link>
      </div>
    );
  }

  const sameEmail = user && user.email.toLowerCase() === (data.email ?? "").toLowerCase();

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3">
        <MailCheck className="size-8 text-primary" aria-hidden />
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Convite para {data.organizationName}</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Emitido para <span className="font-medium text-foreground">{data.email}</span> com o perfil{" "}
            <span className="font-medium text-foreground">{data.roleLabel}</span>.
          </p>
        </div>
      </div>

      {user ? (
        sameEmail ? (
          <Button className="w-full" onClick={() => accept.mutate()} disabled={accept.isPending}>
            {accept.isPending && <Loader2 className="size-4 animate-spin" aria-hidden />}
            Aceitar convite como {user.fullName}
          </Button>
        ) : (
          <Alert role="alert">
            <AlertDescription>
              Você está conectado como {user.email}, mas o convite foi emitido para {data.email}. Saia e entre com a
              conta correta, ou peça um novo convite.
            </AlertDescription>
          </Alert>
        )
      ) : (
        <div className="space-y-3">
          <Button asChild className="w-full">
            <Link to={`/cadastro?convite=${encodeURIComponent(token)}&email=${encodeURIComponent(data.email ?? "")}`}>
              Criar conta e entrar na organização
            </Link>
          </Button>
          <Button asChild variant="outline" className="w-full">
            <Link to="/login" state={{ from: `/convite/${token}` }}>
              Já tenho conta — entrar
            </Link>
          </Button>
        </div>
      )}
    </div>
  );
}
