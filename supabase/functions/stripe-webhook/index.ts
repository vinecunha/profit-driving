import "@supabase/functions-js/edge-runtime.d.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.38.4"
import Stripe from "https://esm.sh/stripe@14.0.0?target=deno"

const stripe = new Stripe(Deno.env.get("STRIPE_SECRET_KEY") || "", {
  apiVersion: "2023-10-16",
  httpClient: Stripe.createFetchHttpClient(),
})

const supabase = createClient(
  Deno.env.get("SUPABASE_URL") || "",
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || ""
)

const endpointSecret = Deno.env.get("STRIPE_WEBHOOK_SECRET") || ""

Deno.serve(async (req) => {
  const signature = req.headers.get("stripe-signature")

  if (!signature) {
    return new Response(JSON.stringify({ error: "No signature" }), { status: 400 })
  }

  let event: Stripe.Event

  try {
    const body = await req.text()
    event = stripe.webhooks.constructEvent(body, signature, endpointSecret)
  } catch (err) {
    console.error(`Webhook signature verification failed: ${err.message}`)
    return new Response(JSON.stringify({ error: err.message }), { status: 400 })
  }

  switch (event.type) {
    case "checkout.session.completed": {
      const session = event.data.object as Stripe.Checkout.Session
      const userId = session.client_reference_id
      const planType = session.metadata?.plan_type || "monthly"

      const now = new Date()
      let expiryDate = new Date()

      switch (planType) {
        case "monthly":
          expiryDate.setMonth(now.getMonth() + 1)
          break
        case "yearly":
          expiryDate.setFullYear(now.getFullYear() + 1)
          break
        case "lifetime":
          expiryDate.setFullYear(now.getFullYear() + 100)
          break
        default:
          expiryDate.setMonth(now.getMonth() + 1)
      }

      await supabase
        .from("profiles")
        .update({
          subscription_status: "active",
          subscription_end: expiryDate.toISOString(),
          trial_end: null,
          has_used_trial: true,
          updated_at: now.toISOString(),
        })
        .eq("id", userId)

      await supabase
        .from("subscriptions")
        .update({
          status: "active",
          ends_at: expiryDate.toISOString(),
        })
        .eq("stripe_session_id", session.id)

      console.log(`Subscription activated for user ${userId} (${planType})`)
      break
    }

    case "customer.subscription.deleted": {
      const subscription = event.data.object as Stripe.Subscription
      const userId = subscription.metadata?.user_id

      if (userId) {
        await supabase
          .from("profiles")
          .update({
            subscription_status: "canceled",
            subscription_end: null,
          })
          .eq("id", userId)
      }
      break
    }

    case "invoice.payment_failed": {
      const invoice = event.data.object as Stripe.Invoice
      console.log(`Payment failed for ${invoice.customer_email}`)
      break
    }
  }

  return new Response(JSON.stringify({ received: true }), {
    status: 200,
    headers: { "Content-Type": "application/json" },
  })
})
